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
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.Feature;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationListener;
import pit12.feature.relation.api.RelationLookup;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.pit.PitContext;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentListener;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceListener;
import pit12.shared.rendering.UiRenderState;

public final class PlayerListFeature implements Feature, PlayerEquipmentListener,
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
    private final UiRenderState renderState = new UiRenderState();
    private PlayerListSnapshot snapshot = PlayerListSnapshot.empty();
    private long lastTabSignature;
    private boolean snapshotDirty = true;
    private int ticksSinceUpdate = UPDATE_INTERVAL_TICKS;
    private boolean started;

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
        hudRegistry.register(hud);
        equipment.addListener(this);
        relations.addListener(this);
        presence.addListener(this);
        configs.addListener(this);
        MinecraftForge.EVENT_BUS.register(this);
        started = true;
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        configs.removeListener(this);
        presence.removeListener(this);
        relations.removeListener(this);
        equipment.removeListener(this);
        hudRegistry.unregister(hud);
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
        if (changes.affects("playerlist", "enabled")
                || changes.affects("playerlist", "show_held_item")
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
                || changes.affects("playerlist", "player_list.offset_y")) {
            snapshotDirty = true;
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) {
            return;
        }
        if (!config.enabled()) {
            ticksSinceUpdate = UPDATE_INTERVAL_TICKS;
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
        if (event.type != ElementType.ALL || !config.enabled() || minecraft.theWorld == null
                || snapshot.isEmpty()) {
            return;
        }
        ScaledResolution resolution = event.resolution;
        float scale = config.hud().scaleFactor();
        hud.resize(resolution.getScaleFactor() * scale);
        int width = Math.max(1, Math.round(hud.width() * scale));
        int height = Math.max(1, Math.round(hud.height() * scale));
        int x = clamp(config.hud().resolveX(resolution.getScaledWidth(), width), 0,
                Math.max(0, resolution.getScaledWidth() - width));
        int y = clamp(config.hud().resolveY(resolution.getScaledHeight(), height), 0,
                Math.max(0, resolution.getScaledHeight() - height));
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        renderState.begin();
        try {
            hud.render(event.partialTicks, false);
        } finally {
            renderState.end();
            GlStateManager.popMatrix();
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
