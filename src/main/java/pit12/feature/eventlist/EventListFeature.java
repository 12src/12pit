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
package pit12.feature.eventlist;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.Setting;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.hud.HudRenderer;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.lifecycle.ClientLifecycle;

public final class EventListFeature implements ClientLifecycle, ConfigChangeListener {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ConfigCatalog configs;
    private final ClientThread client;
    private final EventListConfig config;
    private final HudRegistry hudRegistry;
    private final EventListHud hud;
    private final HudRenderer hudRenderer = new HudRenderer();
    private final EventFeedClient feed = new EventFeedClient();
    private final List<PitEvent> events = new ArrayList<>();
    private ExecutorService worker;
    private Future<?> request;
    private long generation;
    private long nextRequestAt;
    private long rateLimitedUntil;
    private long retryDelay = 60_000L;
    private long displayedSecond = Long.MIN_VALUE;
    private boolean loaded;
    private boolean failed;
    private boolean started;
    private boolean active;

    public EventListFeature(ConfigCatalog configs, EventListConfig config, HudRegistry hudRegistry,
            CommandRegistry commands) {
        this.configs = configs;
        client = configs.clientThread();
        this.config = config;
        this.hudRegistry = hudRegistry;
        hud = new EventListHud(config, hudRenderer);
        commands.register(new EventListCommand(this).definition());
    }

    List<PitEvent> events(EventType type) {
        List<PitEvent> matching = new ArrayList<>();
        for (PitEvent event : events) {
            if (event.type == type) {
                matching.add(event);
            }
        }
        return matching;
    }

    @Override
    public void start() {
        client.check();
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
        client.check();
        if (!started) {
            return;
        }
        started = false;
        configs.removeListener(this);
        deactivate();
        hudRegistry.unregister(hud);
    }

    private void activate() {
        active = true;
        generation++;
        nextRequestAt = rateLimitedUntil;
        rebuild(System.currentTimeMillis());
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void deactivate() {
        active = false;
        generation++;
        MinecraftForge.EVENT_BUS.unregister(this);
        if (request != null) {
            request.cancel(true);
            request = null;
        }
        if (worker != null) {
            worker.shutdownNow();
        }
        events.clear();
        loaded = false;
        failed = false;
        retryDelay = 60_000L;
        displayedSecond = Long.MIN_VALUE;
        hud.snapshot(EventListSnapshot.build(events, config, System.currentTimeMillis(), "", true));
        hud.close();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started) {
            return;
        }
        for (Setting<?> setting : config.settings()) {
            if (changes.affects("eventlist", setting.id())) {
                if (config.enabled() && !active) {
                    activate();
                } else if (!config.enabled() && active) {
                    deactivate();
                } else {
                    rebuild(System.currentTimeMillis());
                }
                return;
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (!active || event.phase != Phase.START || minecraft.theWorld == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (request == null && now >= nextRequestAt) {
            fetch();
        }
        if (now / 1000L != displayedSecond) {
            events.removeIf(scheduled -> scheduled.endsAt <= now);
            rebuild(now);
        }
    }

    private void fetch() {
        if (worker != null && worker.isShutdown()) {
            // Java's HTTP reads may finish only at their timeout after interruption.
            if (!worker.isTerminated()) {
                return;
            }
            worker = null;
        }
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "12pit-event-list");
                thread.setDaemon(true);
                return thread;
            });
        }
        long requestGeneration = generation;
        request = worker.submit(() -> {
            EventFeedClient.Result result = feed.fetch();
            if (!Thread.currentThread().isInterrupted()) {
                client.execute(() -> accept(requestGeneration, result));
            }
        });
    }

    private void accept(long requestGeneration, EventFeedClient.Result result) {
        if (!started || !active || generation != requestGeneration) {
            return;
        }
        request = null;
        long now = System.currentTimeMillis();
        rateLimitedUntil = Math.max(rateLimitedUntil, result.retryAt);
        failed = result.events == null;
        if (failed) {
            nextRequestAt = Math.max(now + retryDelay, rateLimitedUntil);
            retryDelay = Math.min(1_800_000L, retryDelay * 2L);
        } else {
            Map<String, PitEvent> merged = new LinkedHashMap<>();
            for (PitEvent event : events) {
                if (event.timestamp <= now && event.endsAt > now) {
                    merged.put(event.type.id + ":" + event.timestamp, event);
                }
            }
            for (PitEvent event : result.events) {
                if (event.endsAt > now) {
                    merged.put(event.type.id + ":" + event.timestamp, event);
                }
            }
            events.clear();
            events.addAll(merged.values());
            events.sort(Comparator.comparingLong(event -> event.timestamp));
            loaded = true;
            retryDelay = 60_000L;
            long coverage = events.isEmpty() ? 0L : events.get(events.size() - 1).timestamp - now;
            nextRequestAt = Math.max(now + (coverage >= 10_800_000L ? 1_800_000L : 300_000L),
                    rateLimitedUntil);
        }
        rebuild(now);
    }

    private void rebuild(long now) {
        String message = failed ? "Could not load events"
                : loaded ? "Event schedule has ended" : "Loading events\u2026";
        hud.snapshot(EventListSnapshot.build(events, config, now, message, true));
        displayedSecond = now / 1000L;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!active || event.type != ElementType.ALL || hudRegistry.editing()
                || minecraft.theWorld == null) {
            return;
        }
        hudRenderer.render(hud, HudRenderer.layout(hud, event.resolution.getScaledWidth(),
                event.resolution.getScaledHeight(), event.resolution.getScaleFactor(), false),
                event.partialTicks, false);
    }
}
