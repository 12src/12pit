/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>
 *
 * 12pit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 12pit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with 12pit. If not, see <https://www.gnu.org/licenses/>.
 */
package pit12.feature.ringhelper;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.BlockPos.MutableBlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.PitMap;
import pit12.runtime.pit.PitMid;
import pit12.runtime.session.ClientSession;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.rendering.TextureState;

public final class RingHelperFeature implements ClientLifecycle, ConfigChangeListener {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final RingHelperConfig config;
    private final ClientSession session;
    private final PitContext pitContext;
    private final RingRenderer renderer;
    private final MutableBlockPos probeBlock = new MutableBlockPos();
    private List<BlockPos> targets = new ArrayList<>();
    private List<BlockPos> nextTargets = new ArrayList<>();
    private final Runnable sessionChanged = this::reset;
    private WorldClient world;
    private long geometryRevision;
    private boolean started;
    private boolean active;

    public RingHelperFeature(Minecraft minecraft, ConfigCatalog configs, RingHelperConfig config,
            ClientSession session, PitContext pitContext, TextureState textures) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.pitContext = pitContext;
        renderer = new RingRenderer(textures);
    }

    @Override
    public void start() {
        session.checkThread();
        if (started) {
            return;
        }
        started = true;
        configs.addListener(this);
        updateActivation();
    }

    @Override
    public void stop() {
        session.checkThread();
        if (!started) {
            return;
        }
        started = false;
        configs.removeListener(this);
        updateActivation();
    }

    private void updateActivation() {
        boolean enabled = started && config.enabled();
        if (enabled == active) {
            return;
        }
        active = enabled;
        if (enabled) {
            session.addListener(sessionChanged);
            MinecraftForge.EVENT_BUS.register(this);
        } else {
            MinecraftForge.EVENT_BUS.unregister(this);
            session.removeListener(sessionChanged);
        }
        reset();
    }

    private void reset() {
        world = null;
        targets.clear();
        nextTargets.clear();
        geometryRevision++;
        renderer.clear();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (started && (changes.affects("ringhelper", "enabled")
                || changes.affects("ringhelper", "holding_blocks_only"))) {
            updateActivation();
            targets.clear();
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (!active || event.phase != Phase.END) {
            return;
        }
        session.checkThread();
        session.refresh();
        world = session.world();
        nextTargets.clear();
        if (world == null || (config.color() >>> 24) == 0 || !shouldShow()) {
            targets.clear();
            return;
        }
        PitMap currentMap = pitContext.current().map();
        PitMid mid = currentMap.mid();
        if (mid == null) {
            targets.clear();
            return;
        }
        int baseY = currentMap.spawnY() - 2;
        probeBlock.set(0, baseY, 0);
        if (!world.isBlockLoaded(probeBlock)) {
            targets.clear();
            return;
        }
        while (baseY >= 0 && world.isAirBlock(probeBlock)) {
            baseY--;
            probeBlock.set(0, baseY, 0);
        }
        if (baseY < 0) {
            targets.clear();
            return;
        }
        List<BlockPos> edgeColumns = mid.edgeColumns();
        for (BlockPos column : edgeColumns) {
            probeBlock.set(column.getX(), baseY + 2, column.getZ());
            if (world.isBlockLoaded(probeBlock) && world.isAirBlock(probeBlock)) {
                int targetIndex = nextTargets.size();
                BlockPos previous = targetIndex < targets.size() ? targets.get(targetIndex) : null;
                nextTargets.add(previous != null && previous.getX() == column.getX()
                        && previous.getY() == baseY && previous.getZ() == column.getZ() ? previous
                                : new BlockPos(column.getX(), baseY, column.getZ()));
            }
        }
        if (!targets.equals(nextTargets)) {
            List<BlockPos> previous = targets;
            targets = nextTargets;
            nextTargets = previous;
            geometryRevision++;
        }
    }

    private boolean shouldShow() {
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null) {
            return false;
        }
        if (!config.holdingBlocksOnly()) {
            return true;
        }
        ItemStack held = player.getHeldItem();
        return held != null && held.stackSize > 0 && held.getItem() instanceof ItemBlock;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (active && world == minecraft.theWorld && !targets.isEmpty() && shouldShow()) {
            renderer.render(minecraft, targets, config.color(), geometryRevision);
        }
    }
}
