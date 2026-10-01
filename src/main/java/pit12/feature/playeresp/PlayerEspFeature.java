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
package pit12.feature.playeresp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationListener;
import pit12.feature.relation.api.RelationLookup;
import pit12.feature.relation.api.RelationReadiness;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceListener;
import pit12.runtime.session.ClientSession;
import pit12.shared.lifecycle.ClientLifecycle;

public final class PlayerEspFeature
        implements ClientLifecycle, ConfigChangeListener, RelationListener, TabPresenceListener {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ConfigCatalog configs;
    private final PlayerEspConfig config;
    private final ClientSession session;
    private final TabPresence presence;
    private final RelationLookup relations;
    private final PlayerEspRenderer renderer;
    private final Runnable sessionChanged = this::onSessionChanged;
    private final Map<Integer, EntityPlayer> candidates = new LinkedHashMap<>();
    private final List<Target> targets = new ArrayList<>();
    private WorldClient world;
    private boolean targetsDirty;
    private boolean started;
    private boolean active;

    public PlayerEspFeature(ConfigCatalog configs, PlayerEspConfig config, ClientSession session,
            TabPresence presence, RelationLookup relations) {
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.presence = presence;
        this.relations = relations;
        renderer = new PlayerEspRenderer(config);
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
            presence.addListener(this);
            relations.addListener(this);
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
        relations.removeListener(this);
        presence.removeListener(this);
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
            for (EntityPlayer player : world.playerEntities) {
                candidates.put(player.getEntityId(), player);
            }
        }
        targetsDirty = true;
        rebuildTargets();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoin(EntityJoinWorldEvent event) {
        if (!active || !event.world.isRemote || !(event.entity instanceof EntityPlayer)) {
            return;
        }
        session.checkThread();
        session.refresh();
        if (event.world == world) {
            EntityPlayer player = (EntityPlayer) event.entity;
            if (candidates.put(player.getEntityId(), player) != player) {
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
        Iterator<Map.Entry<Integer, EntityPlayer>> iterator = candidates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, EntityPlayer> entry = iterator.next();
            EntityPlayer player = entry.getValue();
            if (player.isDead || world.getEntityByID(entry.getKey()) != player) {
                iterator.remove();
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
        if (world == null || relations.readiness() != RelationReadiness.READY) {
            return;
        }
        for (EntityPlayer player : candidates.values()) {
            if (player == minecraft.thePlayer || player.isDead
                    || !presence.contains(player.getUniqueID())) {
                continue;
            }
            Relation relation = relations.relationOf(player.getUniqueID());
            if (config.shows(relation)) {
                targets.add(new Target(player, relation));
            }
        }
    }

    @Override
    public void onRelationsLoaded() {
        targetsDirty = true;
    }

    @Override
    public void onRelationChanged(UUID playerId, Relation previous, Relation current) {
        targetsDirty = true;
    }

    @Override
    public void onPlayerSeen(UUID playerId, String name, boolean joined) {
        targetsDirty = true;
    }

    @Override
    public void onPlayerLeft(UUID playerId) {
        targetsDirty = true;
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started || !(changes.affects("playeresp", "enabled")
                || changes.affects("playeresp", "show_friend")
                || changes.affects("playeresp", "show_enemy")
                || changes.affects("playeresp", "show_other"))) {
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
        final EntityPlayer player;
        final Relation relation;

        Target(EntityPlayer player, Relation relation) {
            this.player = player;
            this.relation = relation;
        }
    }
}
