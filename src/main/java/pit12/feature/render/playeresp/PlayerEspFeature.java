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
package pit12.feature.render.playeresp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationListener;
import pit12.feature.client.relation.api.RelationLookup;
import pit12.feature.client.relation.api.RelationReadiness;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.player.PlayerEntityListener;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentCache;
import pit12.runtime.player.PlayerEquipmentListener;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceListener;
import pit12.runtime.session.ClientSession;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.performance.HotPath;
import pit12.shared.rendering.TextureState;

public final class PlayerEspFeature implements ClientLifecycle, ConfigChangeListener,
        RelationListener, TabPresenceListener, PlayerEntityListener, PlayerEquipmentListener {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ConfigCatalog configs;
    private final PlayerEspConfig config;
    private final ClientSession session;
    private final TabPresence presence;
    private final RelationLookup relations;
    private final PlayerEntityTracker players;
    private final PlayerEquipmentAccess equipment;
    private final PlayerEspRenderer renderer;
    private final Map<UUID, Target> candidates = new HashMap<>();
    private final List<Target> targets = new ArrayList<>();
    private WorldClient world;
    private boolean started;
    private boolean active;

    public PlayerEspFeature(ConfigCatalog configs, PlayerEspConfig config, ClientSession session,
            TabPresence presence, RelationLookup relations, PlayerEntityTracker players,
            PlayerEquipmentAccess equipment, TextureState textures) {
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.presence = presence;
        this.relations = relations;
        this.players = players;
        this.equipment = equipment;
        renderer = new PlayerEspRenderer(config, textures);
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
            presence.addListener(this);
            relations.addListener(this);
            equipment.addListener(this);
            players.addListener(this);
            MinecraftForge.EVENT_BUS.register(this);
            onPlayerEntitiesReset();
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
        players.removeListener(this);
        equipment.removeListener(this);
        relations.removeListener(this);
        presence.removeListener(this);
        world = null;
        candidates.clear();
        targets.clear();
        renderer.clear();
    }

    @Override
    public void onPlayerEntitiesReset() {
        world = session.world();
        candidates.clear();
        targets.clear();
        renderer.clear();
        if (world != null) {
            for (EntityPlayer player : world.playerEntities) {
                onPlayerEntityAdded(player);
            }
        }
    }

    @Override
    public void onPlayerEntityAdded(EntityPlayer player) {
        if (player == minecraft.thePlayer || player.isDead) {
            return;
        }
        UUID playerId = player.getUniqueID();
        candidates.put(playerId, new Target(player));
        updateTarget(playerId);
    }

    @Override
    public void onPlayerEntityRemoved(EntityPlayer player) {
        Target target = candidates.remove(player.getUniqueID());
        if (target != null) {
            removeTarget(target);
        }
    }

    private void rebuildTargets() {
        for (UUID playerId : candidates.keySet()) {
            updateTarget(playerId);
        }
    }

    private void updateTarget(UUID playerId) {
        Target target = candidates.get(playerId);
        if (target == null) {
            return;
        }
        int color = 0;
        if (presence.contains(playerId)) {
            Relation relation = relations.readiness() == RelationReadiness.READY
                    ? relations.relationOf(playerId)
                    : null;
            color = config.color(relation,
                    config.hasEquipmentTargets() ? equipment.loadedEquipment(playerId) : null);
        }
        target.color = color;
        if ((color >>> 24) != 0) {
            if (target.index < 0) {
                target.index = targets.size();
                targets.add(target);
            }
        } else {
            removeTarget(target);
        }
    }

    private void removeTarget(Target target) {
        if (target.index < 0) {
            return;
        }
        Target last = targets.remove(targets.size() - 1);
        if (last != target) {
            targets.set(target.index, last);
            last.index = target.index;
        }
        target.index = -1;
    }

    @Override
    public void onRelationsLoaded() {
        rebuildTargets();
    }

    @Override
    public void onRelationChanged(UUID playerId, Relation previous, Relation current) {
        updateTarget(playerId);
    }

    @Override
    public void onPlayerSeen(UUID playerId, String name, boolean joined) {
        updateTarget(playerId);
    }

    @Override
    public void onPlayerLeft(UUID playerId) {
        updateTarget(playerId);
    }

    @Override
    public void onPlayerEquipmentChanged(UUID playerId, int changedSlots, long revision) {
        if (config.hasEquipmentTargets() && (changedSlots & PlayerEquipmentCache.LEGGINGS) != 0) {
            updateTarget(playerId);
        }
    }

    @Override
    public void onPlayerEquipmentRemoved(UUID playerId) {
        if (config.hasEquipmentTargets()) {
            updateTarget(playerId);
        }
    }

    @Override
    public void onPlayerEquipmentReset() {
        if (config.hasEquipmentTargets()) {
            rebuildTargets();
        }
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started || !(changes.affects("playeresp", "enabled")
                || changes.affects("playeresp", "show_friend")
                || changes.affects("playeresp", "show_enemy")
                || changes.affects("playeresp", "show_other")
                || changes.affects("playeresp", "show_venom")
                || changes.affects("playeresp", "show_bounty_hunter")
                || changes.affects("playeresp", "friend_color")
                || changes.affects("playeresp", "enemy_color")
                || changes.affects("playeresp", "other_color")
                || changes.affects("playeresp", "venom_color")
                || changes.affects("playeresp", "bounty_hunter_color")
                || changes.affects("playeresp", "priority"))) {
            return;
        }
        updateActivation();
        if (active) {
            rebuildTargets();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @HotPath
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (active && world != null && world == minecraft.theWorld && !targets.isEmpty()) {
            renderer.render(minecraft, targets, event.partialTicks);
        }
    }

    static final class Target {
        final EntityPlayer player;
        int color;
        int index = -1;

        Target(EntityPlayer player) {
            this.player = player;
        }
    }
}
