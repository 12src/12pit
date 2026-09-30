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
package pit12.feature.playerlist;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationListener;
import pit12.feature.relation.api.RelationLookup;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.hud.HudRenderer;
import pit12.runtime.pit.PitContext;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentListener;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceListener;
import pit12.shared.lifecycle.ClientLifecycle;

public final class PlayerListFeature implements ClientLifecycle, PlayerEquipmentListener,
        ConfigChangeListener, RelationListener, TabPresenceListener {
    static final int UPDATE_INTERVAL_TICKS = 2;
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ConfigCatalog configs;
    private final PlayerListConfig config;
    private final PlayerEquipmentAccess equipment;
    private final RelationLookup relations;
    private final TabPresence presence;
    private final PlayerListBuilder builder;
    private final PlayerListHud hud;
    private final HudRegistry hudRegistry;
    private final HudRenderer hudRenderer = new HudRenderer();
    private PlayerListSnapshot snapshot = PlayerListSnapshot.empty();
    private long lastTabSignature;
    private boolean snapshotDirty = true;
    private int ticksSinceUpdate = UPDATE_INTERVAL_TICKS;
    private boolean started;
    private boolean active;

    public PlayerListFeature(ConfigCatalog configs, PlayerListConfig config,
            PlayerEquipmentAccess equipment, PitContext pitContext, HudRegistry hudRegistry,
            RelationLookup relations, TabPresence presence) {
        this.configs = configs;
        this.config = config;
        this.equipment = equipment;
        this.relations = relations;
        this.presence = presence;
        this.hudRegistry = hudRegistry;
        builder = new PlayerListBuilder(minecraft, equipment, pitContext, config, relations);
        hud = new PlayerListHud(config);
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        hudRegistry.register(hud);
        configs.addListener(this);
        if (config.enabled()) {
            activate();
        }
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        configs.removeListener(this);
        deactivate();
        hudRegistry.unregister(hud);
        hud.close();
    }

    private void activate() {
        active = true;
        snapshotDirty = true;
        ticksSinceUpdate = UPDATE_INTERVAL_TICKS;
        try {
            equipment.addListener(this);
            relations.addListener(this);
            presence.addListener(this);
            MinecraftForge.EVENT_BUS.register(this);
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
        presence.removeListener(this);
        relations.removeListener(this);
        equipment.removeListener(this);
        snapshot = PlayerListSnapshot.empty();
        hud.snapshot(snapshot);
        hud.close();
        ticksSinceUpdate = UPDATE_INTERVAL_TICKS;
        snapshotDirty = true;
    }

    @Override
    public void onPlayerEquipmentChanged(java.util.UUID playerId, int changedSlots, long revision) {
        snapshotDirty = true;
    }

    @Override
    public void onPlayerEquipmentRemoved(java.util.UUID playerId) {
        snapshotDirty = true;
    }

    @Override
    public void onPlayerEquipmentReset() {
        snapshotDirty = true;
    }

    @Override
    public void onRelationsLoaded() {
        snapshotDirty = true;
    }

    @Override
    public void onRelationChanged(UUID playerId, Relation previous, Relation current) {
        snapshotDirty = true;
    }

    @Override
    public void onPlayerSeen(UUID playerId, String name, boolean joined) {
        snapshotDirty = true;
    }

    @Override
    public void onPlayerLeft(UUID playerId) {
        snapshotDirty = true;
    }

    @Override
    public void onTabDisplayChanged() {
        snapshotDirty = true;
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started) {
            return;
        }
        if (changes.affects("playerlist", "enabled")) {
            if (config.enabled() && !active) {
                activate();
            } else if (!config.enabled()) {
                deactivate();
            }
        }
        if (active && (changes.affects("playerlist", "show_held_item")
                || changes.affects("playerlist", "show_leggings")
                || changes.affects("playerlist", "enchantment_format")
                || changes.affects("playerlist", "show_distance")
                || changes.affects("playerlist", "show_direction")
                || changes.affects("playerlist", "show_spawn")
                || changes.affects("playerlist", "show_group_name")
                || changes.affects("playerlist", "show_friend")
                || changes.affects("playerlist", "show_enemy")
                || changes.affects("playerlist", "show_regularity")
                || changes.affects("playerlist", "show_dark")
                || changes.affects("playerlist", "show_bounty_hunter")
                || changes.affects("playerlist", "use_vanilla_font")
                || changes.affects("playerlist", "player_list.text_shadow")
                || changes.affects("playerlist", "player_list.scale")
                || changes.affects("playerlist", "player_list.anchor")
                || changes.affects("playerlist", "player_list.offset_x")
                || changes.affects("playerlist", "player_list.offset_y"))) {
            snapshotDirty = true;
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) {
            return;
        }
        ticksSinceUpdate++;
        if (ticksSinceUpdate < UPDATE_INTERVAL_TICKS) {
            return;
        }
        ticksSinceUpdate = 0;
        boolean movingColumns = config.showDistance() || config.showDirection();
        if (snapshotDirty || movingColumns) {
            snapshot = builder.build();
            hud.snapshot(snapshot);
            snapshotDirty = false;
            if (!movingColumns) {
                lastTabSignature = builder.tabSignature();
            }
            return;
        }
        long tabSignature = builder.tabSignature();
        if (tabSignature != lastTabSignature) {
            snapshot = builder.build();
            hud.snapshot(snapshot);
            lastTabSignature = tabSignature;
            snapshotDirty = false;
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != ElementType.ALL || hudRegistry.editing() || minecraft.theWorld == null
                || snapshot.isEmpty()) {
            return;
        }
        ScaledResolution resolution = event.resolution;
        hudRenderer.render(
                hud, HudRenderer.layout(hud, resolution.getScaledWidth(),
                        resolution.getScaledHeight(), resolution.getScaleFactor(), false),
                event.partialTicks, false);
    }
}
