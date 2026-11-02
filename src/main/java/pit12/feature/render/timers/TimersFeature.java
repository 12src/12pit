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
package pit12.feature.render.timers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationListener;
import pit12.feature.client.relation.api.RelationLookup;
import pit12.feature.client.relation.api.RelationReadiness;
import pit12.feature.render.timers.TimersHud.Group;
import pit12.feature.render.timers.TimersHud.TimerType;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.Setting;
import pit12.runtime.hud.HudBounds;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.hud.HudRenderer;
import pit12.runtime.player.PlayerNameCache;
import pit12.runtime.session.ClientSession;
import pit12.runtime.timers.PlayerTimers;
import pit12.runtime.timers.TimerDemand;
import pit12.runtime.timers.TimerKind;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.performance.HotPath;

public final class TimersFeature
        implements ClientLifecycle, ConfigChangeListener, RelationListener {
    private static final TimerType[] TYPES = TimerType.values();
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final TimersConfig config;
    private final ClientSession session;
    private final RelationLookup relations;
    private final PlayerNameCache playerNames;
    private final HudRegistry hudRegistry;
    private final PlayerTimers timers;
    private final HudRenderer renderer = new HudRenderer();
    private final TimersHud[] huds = new TimersHud[TYPES.length + 1];
    private final List<TimersHud> activeHuds = new ArrayList<>();
    private final EnumMap<TimerType, Map<UUID, TimersHud.Row>> cachedRows =
            new EnumMap<>(TimerType.class);
    private final PlayerTimers.Visitor visitor = this::appendTimer;
    private List<TimersHud.Row> rows = new ArrayList<>();
    private List<TimersHud.Row> pendingRows = new ArrayList<>();
    private long nextRefresh;
    private long lastRevision;
    private long refresh;
    private long now;
    private int selection;
    private boolean orderChanged;
    private boolean started;
    private boolean active;

    public TimersFeature(Minecraft minecraft, ConfigCatalog configs, TimersConfig config,
            ClientSession session, RelationLookup relations, PlayerNameCache playerNames,
            HudRegistry hudRegistry, PlayerTimers timers) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.relations = relations;
        this.playerNames = playerNames;
        this.hudRegistry = hudRegistry;
        this.timers = timers;
        timers.configure(config, id -> {
            if (relations.readiness() != RelationReadiness.READY)
                return TimerDemand.REMOTE;
            Relation relation = relations.relationOf(id);
            return relation == Relation.FRIEND ? TimerDemand.FRIEND
                    : relation == Relation.ENEMY ? TimerDemand.ENEMY : TimerDemand.OTHER;
        });
        huds[0] = new TimersHud(minecraft, config, renderer, null);
        for (TimerType type : TYPES) {
            huds[type.ordinal() + 1] = new TimersHud(minecraft, config, renderer, type);
            cachedRows.put(type, new HashMap<>());
        }
    }

    @Override
    public void start() {
        session.checkThread();
        started = true;
        configs.addListener(this);
        relations.addListener(this);
        updateHudLayout();
        updateActivation();
    }

    @Override
    public void stop() {
        session.checkThread();
        started = false;
        configs.removeListener(this);
        relations.removeListener(this);
        updateActivation();
        for (TimersHud hud : huds)
            hudRegistry.unregister(hud);
        activeHuds.clear();
    }

    private void updateHudLayout() {
        activeHuds.clear();
        for (TimersHud hud : huds) {
            if (hud.active()) {
                hudRegistry.register(hud);
                activeHuds.add(hud);
            } else {
                hudRegistry.unregister(hud);
            }
        }
        updateHudRows();
    }

    private void updateHudRows() {
        for (TimersHud hud : huds) {
            hud.rows.clear();
            hud.invalidate();
        }
        for (int index = 0, size = rows.size(); index < size; index++) {
            TimersHud.Row row = rows.get(index);
            huds[config.hudGroups.get(row.type).independent() ? row.type.ordinal() + 1 : 0].rows
                    .add(row);
        }
    }

    private void updateActivation() {
        selection = started ? config.timerDemand() : 0;
        timers.setDemand(this, selection);
        boolean enabled = selection != 0;
        if (active == enabled)
            return;
        active = enabled;
        if (active) {
            playerNames.retain(this);
            MinecraftForge.EVENT_BUS.register(this);
        } else {
            MinecraftForge.EVENT_BUS.unregister(this);
            playerNames.release(this);
            rows.clear();
            pendingRows.clear();
            for (Map<UUID, TimersHud.Row> cache : cachedRows.values())
                cache.clear();
            updateHudRows();
        }
        nextRefresh = 0;
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started)
            return;
        boolean affected = false;
        for (Setting<?> setting : config.settings())
            if (changes.affects("debufftimers", setting.id())) {
                affected = true;
                break;
            }
        if (!affected)
            return;
        for (TimersHud hud : huds)
            hud.invalidate();
        updateActivation();
        for (TimerType type : TYPES)
            if (changes.affects("debufftimers", "debuff_timers." + type.id + ".independent")) {
                updateHudLayout();
                break;
            }
        if (changes.affects("debufftimers", "jump_boost_reset_on_low_jump")
                || changes.affects("debufftimers", "jump_boost_sound_required"))
            timers.settingsChanged();
        nextRefresh = 0;
    }

    @Override
    public void onRelationsLoaded() {
        timers.refreshAudience();
        nextRefresh = 0;
    }

    @Override
    public void onRelationChanged(UUID id, Relation previous, Relation current) {
        timers.refreshAudience();
        nextRefresh = 0;
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase != Phase.END)
            return;
        now = System.nanoTime();
        if (now < nextRefresh && timers.revision() == lastRevision) {
            boolean expired = false;
            for (int index = 0, size = activeHuds.size(); index < size; index++) {
                TimersHud hud = activeHuds.get(index);
                boolean changed = false;
                for (int rowIndex = 0, count = hud.rows.size(); rowIndex < count; rowIndex++) {
                    TimersHud.Row row = hud.rows.get(rowIndex);
                    expired |= now >= row.until;
                    changed |= row.updateTime(row.until - now);
                }
                if (changed)
                    hud.invalidate();
            }
            if (!expired)
                return;
        }
        nextRefresh = now + 100_000_000L;
        lastRevision = timers.revision();
        refresh++;
        orderChanged = false;
        pendingRows.clear();
        timers.visit(selection, visitor);
        for (Map<UUID, TimersHud.Row> cache : cachedRows.values())
            cache.values().removeIf(row -> row.refresh != refresh);
        if (orderChanged || pendingRows.size() != rows.size()) {
            pendingRows.sort(Comparator.comparing((TimersHud.Row row) -> row.type)
                    .thenComparing(row -> row.group).thenComparingLong(row -> row.until));
            List<TimersHud.Row> previous = rows;
            rows = pendingRows;
            pendingRows = previous;
        }
        updateHudRows();
    }

    private void appendTimer(TimerKind kind, EntityPlayer player, long until, int audience) {
        if (until <= now)
            return;
        TimerType type;
        switch (kind) {
            case PIN:
                type = TimerType.PIN;
                break;
            case VENOM_WEARER:
            case VENOM_TARGET:
                type = TimerType.VENOM;
                break;
            case JUMP_BOOST:
                type = TimerType.JUMP_BOOST;
                break;
            default:
                type = TimerType.TELEBOW;
        }
        UUID id = player.getUniqueID();
        Map<UUID, TimersHud.Row> cache = cachedRows.get(type);
        TimersHud.Row row = cache.get(id);
        Group group = audience == TimerDemand.OWN ? Group.OWN
                : audience == TimerDemand.FRIEND ? Group.FRIEND
                        : audience == TimerDemand.ENEMY ? Group.ENEMY : Group.OTHER;
        String name = playerNames.shortName(id);
        if (name == null)
            name = player.getDisplayName().getFormattedText();
        if (row == null) {
            row = new TimersHud.Row(type, group, name, until - now);
            cache.put(id, row);
            orderChanged = true;
        } else {
            orderChanged |= row.group != group || row.until != until;
            row.update(group, name, until - now);
        }
        row.refresh = refresh;
        row.until = until;
        pendingRows.add(row);
    }

    @SubscribeEvent
    @HotPath
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || rows.isEmpty()
                || hudRegistry.editing() || minecraft.theWorld == null
                || minecraft.gameSettings.hideGUI || !HudRenderer.visible(config.hud))
            return;
        ScaledResolution resolution = event.resolution;
        renderer.beginBatch();
        try {
            for (int index = 0, size = activeHuds.size(); index < size; index++) {
                TimersHud hud = activeHuds.get(index);
                if (hud.rows.isEmpty()) {
                    continue;
                }
                HudBounds bounds = HudRenderer.layout(hud, resolution.getScaledWidth(),
                        resolution.getScaledHeight(), resolution.getScaleFactor(), false);
                hud.visibleHeight = (resolution.getScaledHeight() - bounds.y) / bounds.scale;
                renderer.render(hud, bounds, event.partialTicks, false);
            }
        } finally {
            renderer.endBatch();
        }
    }
}
