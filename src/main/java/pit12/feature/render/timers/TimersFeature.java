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
import pit12.feature.client.relation.api.PlayerAudience;
import pit12.feature.provider.telebow.api.TelebowPrediction;
import pit12.feature.provider.timertracking.api.TimerKind;
import pit12.feature.provider.timertracking.api.TimerTracking;
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
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.performance.HotPath;

public final class TimersFeature implements ClientLifecycle, ConfigChangeListener {
    private static final TimerType[] TYPES = TimerType.values();
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final TimersConfig config;
    private final ClientSession session;
    private final PlayerNameCache playerNames;
    private final HudRegistry hudRegistry;
    private final TimerTracking timers;
    private final TelebowPrediction prediction;
    private final HudRenderer renderer = new HudRenderer();
    private final TimersHud[] huds = new TimersHud[TYPES.length + 1];
    private final List<TimersHud> activeHuds = new ArrayList<>();
    private final EnumMap<TimerType, Map<UUID, TimersHud.Row>> cachedRows =
            new EnumMap<>(TimerType.class);
    private final TimerTracking.Visitor visitor = this::appendTimer;
    private final TelebowPrediction.CooldownVisitor cooldownVisitor =
            (player, until, audience) -> appendRow(TimerType.TELEBOW, player, until, audience);
    private List<TimersHud.Row> rows = new ArrayList<>();
    private List<TimersHud.Row> pendingRows = new ArrayList<>();
    private long nextRefresh;
    private long lastRevision;
    private long lastCooldownRevision;
    private long refresh;
    private long now;
    private int selection;
    private int cooldownAudience;
    private boolean orderChanged;
    private boolean started;
    private boolean active;

    public TimersFeature(Minecraft minecraft, ConfigCatalog configs, TimersConfig config,
            ClientSession session, PlayerNameCache playerNames, HudRegistry hudRegistry,
            TimerTracking timers, TelebowPrediction prediction) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.playerNames = playerNames;
        this.hudRegistry = hudRegistry;
        this.timers = timers;
        this.prediction = prediction;
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
        updateHudLayout();
        updateActivation();
    }

    @Override
    public void stop() {
        session.checkThread();
        started = false;
        configs.removeListener(this);
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
        cooldownAudience = started ? config.cooldownAudience() : 0;
        prediction.setCooldownDemand(this, cooldownAudience);
        boolean enabled = selection != 0 || cooldownAudience != 0;
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
        nextRefresh = 0;
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase != Phase.END)
            return;
        now = System.nanoTime();
        long revision = selection == 0 ? 0 : timers.revision();
        long cooldownRevision = cooldownAudience == 0 ? 0 : prediction.cooldownRevision();
        if (now < nextRefresh && revision == lastRevision
                && cooldownRevision == lastCooldownRevision) {
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
        lastRevision = revision;
        lastCooldownRevision = cooldownRevision;
        refresh++;
        orderChanged = false;
        pendingRows.clear();
        if (selection != 0)
            timers.visit(selection, visitor);
        if (cooldownAudience != 0)
            prediction.visitCooldowns(cooldownAudience, cooldownVisitor);
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
        TimerType type = kind == TimerKind.PIN ? TimerType.PIN
                : kind == TimerKind.JUMP_BOOST ? TimerType.JUMP_BOOST : TimerType.VENOM;
        appendRow(type, player, until, audience);
    }

    private void appendRow(TimerType type, EntityPlayer player, long until, int audience) {
        if (until <= now)
            return;
        UUID id = player.getUniqueID();
        Map<UUID, TimersHud.Row> cache = cachedRows.get(type);
        TimersHud.Row row = cache.get(id);
        Group group = audience == PlayerAudience.OWN ? Group.OWN
                : audience == PlayerAudience.FRIEND ? Group.FRIEND
                        : audience == PlayerAudience.ENEMY ? Group.ENEMY : Group.OTHER;
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
