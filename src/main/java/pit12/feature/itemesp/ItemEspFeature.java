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
package pit12.feature.itemesp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.session.ClientSession;
import pit12.shared.lifecycle.ClientLifecycle;

public final class ItemEspFeature implements ClientLifecycle, ConfigChangeListener {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ConfigCatalog configs;
    private final ItemEspConfig config;
    private final ClientSession session;
    private final ItemEspRenderer renderer;
    private final Runnable sessionChanged = this::onSessionChanged;
    private final Map<Integer, Target> candidates = new LinkedHashMap<>();
    private final List<Target> targets = new ArrayList<>();
    private WorldClient world;
    private boolean targetsDirty;
    private boolean started;
    private boolean active;

    public ItemEspFeature(ConfigCatalog configs, ItemEspConfig config, ClientSession session) {
        this.configs = configs;
        this.config = config;
        this.session = session;
        renderer = new ItemEspRenderer(config);
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
        deactivate();
    }

    private void updateActivation() {
        if (config.enabled() && config.hasTargets()) {
            if (!active) {
                activate();
            }
        } else {
            deactivate();
        }
    }

    private void activate() {
        active = true;
        try {
            session.addListener(sessionChanged);
            MinecraftForge.EVENT_BUS.register(this);
            onSessionChanged();
        } catch (RuntimeException failure) {
            deactivate();
            throw failure;
        }
    }

    private void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        session.removeListener(sessionChanged);
        world = null;
        candidates.clear();
        targets.clear();
        targetsDirty = false;
        renderer.clear();
    }

    private void onSessionChanged() {
        if (!active) {
            return;
        }
        WorldClient current = session.world();
        if (world == current) {
            return;
        }
        world = current;
        candidates.clear();
        targets.clear();
        renderer.clear();
        if (world != null) {
            for (Entity entity : world.loadedEntityList) {
                if (entity instanceof EntityItem) {
                    EntityItem item = (EntityItem) entity;
                    candidates.put(item.getEntityId(), new Target(item));
                }
            }
        }
        targetsDirty = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoin(EntityJoinWorldEvent event) {
        if (!active || !event.world.isRemote || !(event.entity instanceof EntityItem)) {
            return;
        }
        session.checkThread();
        session.refresh();
        if (event.world == world) {
            EntityItem entity = (EntityItem) event.entity;
            Target previous = candidates.get(entity.getEntityId());
            if (previous == null || previous.entity != entity) {
                candidates.put(entity.getEntityId(), new Target(entity));
                targetsDirty = true;
            }
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (!active || event.phase != Phase.END) {
            return;
        }
        session.checkThread();
        session.refresh();
        if (world == null) {
            return;
        }
        Iterator<Map.Entry<Integer, Target>> iterator = candidates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Target> entry = iterator.next();
            Target target = entry.getValue();
            EntityItem entity = target.entity;
            if (entity.isDead || world.getEntityByID(entry.getKey()) != entity) {
                iterator.remove();
                targetsDirty = true;
                continue;
            }
            // Watcher 10 holds the 1.8.9 item stack and can be null before metadata arrives.
            // getEntityItem() allocates a stone stack when that value is missing.
            ItemStack stack = entity.getDataWatcher().getWatchableObjectItemStack(10);
            Item item = stack == null || stack.stackSize <= 0 ? null : stack.getItem();
            if (target.item != item) {
                target.item = item;
                targetsDirty = true;
            }
        }
        if (targetsDirty) {
            rebuildTargets();
        }
    }

    private void rebuildTargets() {
        targets.clear();
        targetsDirty = false;
        boolean raffle = config.showRaffle();
        boolean gold = config.showGold();
        for (Target target : candidates.values()) {
            if ((raffle && target.item == Items.name_tag)
                    || (gold && target.item == Items.gold_ingot)) {
                targets.add(target);
            }
        }
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started || !(changes.affects("itemesp", "enabled")
                || changes.affects("itemesp", "show_raffle")
                || changes.affects("itemesp", "show_gold"))) {
            return;
        }
        updateActivation();
        if (active) {
            rebuildTargets();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (active && world != null && world == minecraft.theWorld && !targets.isEmpty()) {
            renderer.render(minecraft, targets, event.partialTicks);
        }
    }

    static final class Target {
        final EntityItem entity;
        Item item;

        Target(EntityItem entity) {
            this.entity = entity;
        }
    }
}
