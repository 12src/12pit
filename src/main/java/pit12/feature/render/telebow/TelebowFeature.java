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
package pit12.feature.render.telebow;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationListener;
import pit12.feature.client.relation.api.RelationLookup;
import pit12.feature.client.relation.api.RelationReadiness;
import pit12.feature.render.telebow.api.TelebowCooldowns;
import pit12.runtime.arrow.ArrowListener;
import pit12.runtime.arrow.ArrowTracker;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.pit.PitContext;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.session.ClientSession;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.rendering.TextureState;

public final class TelebowFeature
        implements ClientLifecycle, ConfigChangeListener, ArrowListener, RelationListener {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final TelebowConfig config;
    private final ClientSession session;
    private final ArrowTracker arrows;
    private final RelationLookup relations;
    private final TelebowCooldownTracker cooldowns;
    private final Map<Integer, TelebowPath> paths = new LinkedHashMap<>();
    private final TelebowRenderer renderer;
    private final TelebowAim aim;
    private boolean started;
    private boolean active;
    private boolean aimActive;

    public TelebowFeature(Minecraft minecraft, ConfigCatalog configs, TelebowConfig config,
            ClientSession session, ArrowTracker arrows, RelationLookup relations,
            TextureState textures, PlayerEntityTracker players, PitContext pit) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.arrows = arrows;
        this.relations = relations;
        cooldowns = new TelebowCooldownTracker(configs, config, session, arrows, players, pit);
        renderer = new TelebowRenderer(minecraft, config, textures);
        aim = new TelebowAim(minecraft, config, player -> colorOf(player, true));
    }

    public TelebowCooldowns cooldowns() {
        return cooldowns;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started)
            return;
        started = true;
        cooldowns.start();
        configs.addListener(this);
        updateActivation();
    }

    @Override
    public void stop() {
        session.checkThread();
        started = false;
        configs.removeListener(this);
        updateActivation();
        cooldowns.stop();
    }

    private void updateActivation() {
        boolean prediction = config.own.get() && (config.ownColor.get() >>> 24) != 0
                || config.friends.get() && (config.friendColor.get() >>> 24) != 0
                || config.enemies.get() && (config.enemyColor.get() >>> 24) != 0
                || config.others.get() && (config.otherColor.get() >>> 24) != 0;
        aimActive = started && config.enabled() && config.aimPoints.get()
                && (config.aimFriends.get() && (config.friendColor.get() >>> 24) != 0
                        || config.aimEnemies.get() && (config.enemyColor.get() >>> 24) != 0
                        || config.aimOthers.get() && (config.otherColor.get() >>> 24) != 0);
        boolean enabled = started && config.enabled() && (prediction || aimActive);
        if (enabled == active)
            return;
        active = enabled;
        if (active) {
            arrows.addListener(this);
            relations.addListener(this);
            MinecraftForge.EVENT_BUS.register(this);
        } else {
            MinecraftForge.EVENT_BUS.unregister(this);
            relations.removeListener(this);
            arrows.removeListener(this);
            onArrowsReset();
        }
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started || !(changes.affects("telebow", "enabled") || changes.affects("telebow", "own")
                || changes.affects("telebow", "friends") || changes.affects("telebow", "enemies")
                || changes.affects("telebow", "others") || changes.affects("telebow", "own_color")
                || changes.affects("telebow", "friend_color")
                || changes.affects("telebow", "enemy_color")
                || changes.affects("telebow", "other_color")
                || changes.affects("telebow", "aim_points")
                || changes.affects("telebow", "aim_friends")
                || changes.affects("telebow", "aim_enemies")
                || changes.affects("telebow", "aim_others")
                || changes.affects("telebow", "aim_tolerance_blocks")
                || changes.affects("telebow", "ignore_cooldown")))
            return;
        updateActivation();
        refreshPaths();
        aim.clear();
    }

    private void refreshPaths() {
        Iterator<TelebowPath> iterator = paths.values().iterator();
        while (iterator.hasNext()) {
            TelebowPath path = iterator.next();
            int color = colorOf(path.shooter, false);
            if ((color >>> 24) == 0
                    && (!config.aimPoints.get() || (colorOf(path.shooter, true) >>> 24) == 0)) {
                renderer.remove(path);
                iterator.remove();
            } else {
                path.color = color;
            }
        }
    }

    @Override
    public void onRelationsLoaded() {
        refreshPaths();
    }

    @Override
    public void onRelationChanged(UUID playerId, Relation previous, Relation current) {
        refreshPaths();
    }

    private int colorOf(EntityPlayer shooter, boolean aiming) {
        if (minecraft.thePlayer == null)
            return 0;
        if (shooter.getUniqueID().equals(minecraft.thePlayer.getUniqueID()))
            return !aiming && config.own.get() ? config.ownColor.get() : 0;
        if (relations.readiness() != RelationReadiness.READY)
            return 0;
        switch (relations.relationOf(shooter.getUniqueID())) {
            case FRIEND:
                return (aiming ? config.aimFriends.get() : config.friends.get())
                        ? config.friendColor.get()
                        : 0;
            case ENEMY:
                return (aiming ? config.aimEnemies.get() : config.enemies.get())
                        ? config.enemyColor.get()
                        : 0;
            default:
                return (aiming ? config.aimOthers.get() : config.others.get())
                        ? config.otherColor.get()
                        : 0;
        }
    }

    @Override
    public boolean onArrowSpawn(EntityArrow arrow, EntityPlayer shooter, ItemStack bow,
            boolean sneaking, long lastSneakingNanos) {
        if (!active)
            return false;
        if (config.ignoreCooldown.get()
                && cooldowns.blocksArrow(shooter.getUniqueID(), arrow.getEntityId()))
            return false;
        if (!sneaking && (lastSneakingNanos == 0
                || System.nanoTime() - lastSneakingNanos > config.sneakWindow.get() * 1_000_000L))
            return false;
        int color = colorOf(shooter, false);
        if ((color >>> 24) == 0
                && (!config.aimPoints.get() || (colorOf(shooter, true) >>> 24) == 0))
            return false;
        if (PitEnchantmentReader.levelOf(bow, PitEnchantment.Telebow) == 0)
            return false;
        TelebowPath path = new TelebowPath(arrow, shooter, color);
        TelebowPath previous = paths.put(arrow.getEntityId(), path);
        if (previous != null)
            renderer.remove(previous);
        return true;
    }

    @Override
    public void onArrowRemoved(EntityArrow arrow, boolean serverRemoved) {
        TelebowPath path = paths.get(arrow.getEntityId());
        if (path != null)
            path.finish(System.nanoTime());
    }

    @Override
    public void onArrowCorrected(EntityArrow arrow, boolean velocityChanged) {
        TelebowPath path = paths.get(arrow.getEntityId());
        if (path != null && path.endedAt == 0) {
            path.correct(velocityChanged);
        }
    }

    @Override
    public void onBlockChanged(BlockPos position) {
        for (TelebowPath path : paths.values()) {
            if (path.endedAt != 0 || path.dirty)
                continue;
            if (path.affectedBy(position)) {
                path.dirty = true;
            }
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase != Phase.END)
            return;
        long now = System.nanoTime();
        long lifetime = (config.holdTime.get() + config.fadeTime.get()) * 1_000_000L;
        Iterator<TelebowPath> iterator = paths.values().iterator();
        while (iterator.hasNext()) {
            TelebowPath path = iterator.next();
            if (path.endedAt != 0) {
                if (now - path.endedAt >= lifetime) {
                    renderer.remove(path);
                    iterator.remove();
                }
                continue;
            }
            path.updateProgress();
            if (path.dirty)
                path.predict();
        }
        if (aimActive) {
            if (minecraft.theWorld == session.world())
                aim.update(paths.values());
            else
                aim.clear();
        }
    }

    @Override
    public void onArrowsReset() {
        paths.clear();
        aim.clear();
        renderer.clear();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (paths.isEmpty() && aim.targets.isEmpty() || minecraft.gameSettings.hideGUI
                || minecraft.theWorld != session.world())
            return;
        if (aimActive)
            aim.interpolate(event.partialTicks);
        renderer.render(paths.values(), aim.targets.values(), System.nanoTime());
    }
}
