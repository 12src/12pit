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
package pit12.feature.render.playerlist;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationListener;
import pit12.feature.client.relation.api.RelationLookup;
import pit12.feature.misc.denick.api.DenickNames;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.Setting;
import pit12.runtime.hud.HudBounds;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.hud.HudRenderer;
import pit12.runtime.languages.Languages;
import pit12.runtime.nick.NickTracker;
import pit12.runtime.pit.PitContext;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentListener;
import pit12.runtime.player.PlayerNameCache;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceListener;
import pit12.shared.input.Keybind;
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
    private final PlayerNameCache playerNames;
    private final DenickNames denickNames;
    private final PlayerListBuilder builder;
    private final List<PlayerListHud> huds = new ArrayList<>();
    private final List<PlayerListHud> activeHuds = new ArrayList<>();
    private final FontListener fontListener = new FontListener();
    private final Languages language;
    private final Runnable languageListener;
    private final HudRegistry hudRegistry;
    private final HudRenderer hudRenderer = new HudRenderer();
    private PlayerListSnapshot snapshot = PlayerListSnapshot.empty();
    private boolean snapshotDirty = true;
    private boolean realNamesHeld;
    private final Runnable namesChanged = () -> snapshotDirty = true;
    private int ticksSinceUpdate = UPDATE_INTERVAL_TICKS;
    private boolean started;
    private boolean active;

    public PlayerListFeature(ConfigCatalog configs, PlayerListConfig config,
            PlayerNameCache playerNames, PlayerEntityTracker players,
            PlayerEquipmentAccess equipment, PitContext pitContext, HudRegistry hudRegistry,
            RelationLookup relations, TabPresence presence, Languages language,
            DenickNames denickNames, NickTracker nicks) {
        this.language = language;
        this.configs = configs;
        this.config = config;
        this.equipment = equipment;
        this.relations = relations;
        this.presence = presence;
        this.playerNames = playerNames;
        this.denickNames = denickNames;
        this.hudRegistry = hudRegistry;
        builder = new PlayerListBuilder(minecraft, players, equipment, pitContext, config,
                relations, playerNames, denickNames, nicks);
        huds.add(new PlayerListHud(config, hudRenderer, language, null));
        for (PlayerListGroup group : PlayerListGroup.values()) {
            huds.add(new PlayerListHud(config, hudRenderer, language, group));
        }
        languageListener = () -> {
            for (PlayerListHud hud : huds) {
                hud.localize();
            }
        };
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        MinecraftForge.EVENT_BUS.register(fontListener);
        language.addListener(languageListener);
        updateHudLayout();
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
        MinecraftForge.EVENT_BUS.unregister(fontListener);
        configs.removeListener(this);
        language.removeListener(languageListener);
        deactivate();
        for (PlayerListHud hud : huds) {
            hudRegistry.unregister(hud);
            hud.close();
        }
        activeHuds.clear();
    }

    private void updateHudLayout() {
        activeHuds.clear();
        for (PlayerListHud hud : huds) {
            if (hud.active()) {
                hudRegistry.register(hud);
                activeHuds.add(hud);
            } else {
                hudRegistry.unregister(hud);
            }
        }
    }

    private void activate() {
        active = true;
        snapshotDirty = true;
        ticksSinceUpdate = UPDATE_INTERVAL_TICKS;
        try {
            equipment.addListener(this);
            relations.addListener(this);
            presence.addListener(this);
            playerNames.addListener(namesChanged);
            denickNames.addListener(namesChanged);
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
        playerNames.removeListener(namesChanged);
        denickNames.removeListener(namesChanged);
        relations.removeListener(this);
        equipment.removeListener(this);
        snapshot = PlayerListSnapshot.empty();
        builder.clear();
        for (PlayerListHud hud : huds) {
            hud.snapshot(snapshot);
            hud.close();
        }
        ticksSinceUpdate = UPDATE_INTERVAL_TICKS;
        snapshotDirty = true;
        realNamesHeld = false;
    }

    @Override
    public void onPlayerEquipmentChanged(UUID playerId, int changedSlots, long revision) {
        snapshotDirty = true;
    }

    @Override
    public void onPlayerEquipmentRemoved(UUID playerId) {
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
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started) {
            return;
        }
        for (Setting<?> setting : config.settings()) {
            if (changes.affects("playerlist", setting.id())) {
                for (PlayerListHud hud : huds) {
                    hud.configurationChanged();
                }
                break;
            }
        }
        if (changes.affects("playerlist", config.hud().translateText().id())) {
            languageListener.run();
        }
        for (PlayerListGroup group : PlayerListGroup.values()) {
            if (changes.affects("playerlist", "player_list." + group.id() + ".independent")) {
                updateHudLayout();
                break;
            }
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
                || changes.affects("playerlist", "show_gem")
                || changes.affects("playerlist", "short_player_names")
                || changes.affects("playerlist", "nick_marker")
                || changes.affects("playerlist", "real_names_key")
                || changes.affects("playerlist", "show_distance")
                || changes.affects("playerlist", "show_direction")
                || changes.affects("playerlist", "show_spawn")
                || changes.affects("playerlist", "hide_spawn")
                || changes.affects("playerlist", "show_group_name")
                || changes.affects("playerlist", "show_friend")
                || changes.affects("playerlist", "show_enemy")
                || changes.affects("playerlist", "show_nick")
                || changes.affects("playerlist", "show_regularity")
                || changes.affects("playerlist", "show_dark")
                || changes.affects("playerlist", "show_bounty_hunter")
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
        boolean realNames = minecraft.currentScreen == null && minecraft.inGameHasFocus
                && Keybind.held(config.realNamesKey());
        if (realNames != realNamesHeld) {
            realNamesHeld = realNames;
            snapshotDirty = true;
            ticksSinceUpdate = UPDATE_INTERVAL_TICKS;
        }
        ticksSinceUpdate++;
        if (ticksSinceUpdate < UPDATE_INTERVAL_TICKS) {
            return;
        }
        ticksSinceUpdate = 0;
        boolean updatePositions =
                config.showDistance() || config.showDirection() || config.hideSpawn();
        if (snapshotDirty || updatePositions) {
            boolean rebuildText = snapshotDirty;
            snapshotDirty = false;
            snapshot = builder.build(realNamesHeld, rebuildText);
            for (PlayerListHud hud : huds) {
                hud.snapshot(snapshot);
            }
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != ElementType.ALL || hudRegistry.editing() || minecraft.theWorld == null
                || snapshot.isEmpty() || !HudRenderer.visible(config.hud())) {
            return;
        }
        ScaledResolution resolution = event.resolution;
        hudRenderer.beginBatch();
        try {
            for (PlayerListHud hud : activeHuds) {
                HudBounds bounds = HudRenderer.layout(hud, resolution.getScaledWidth(),
                        resolution.getScaledHeight(), resolution.getScaleFactor(), false);
                if (hud.width() > 0) {
                    hudRenderer.render(hud, bounds, event.partialTicks, false);
                }
            }
        } finally {
            hudRenderer.endBatch();
        }
    }

    // Forge needs this listener class to be public.
    public final class FontListener {
        @SubscribeEvent
        public void onTextureStitch(TextureStitchEvent.Post event) {
            for (PlayerListHud hud : huds) {
                hud.configurationChanged();
            }
        }
    }
}
