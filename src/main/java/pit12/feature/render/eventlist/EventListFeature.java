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
package pit12.feature.render.eventlist;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.Setting;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.hud.HudRenderer;
import pit12.runtime.languages.Languages;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.lifecycle.ClientLifecycle;

public final class EventListFeature implements ClientLifecycle, ConfigChangeListener {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ConfigCatalog configs;
    private final ClientThread client;
    private final EventListConfig config;
    private final CommandRegistry commands;
    private final HudRegistry hudRegistry;
    private final EventListHud hud;
    private final FontListener fontListener = new FontListener();
    private final Languages language;
    private final Runnable languageListener;
    private final HudRenderer hudRenderer = new HudRenderer();
    private final EventFeedClient feed = new EventFeedClient();
    private final List<PitEvent> events = new ArrayList<>();
    // Reuse the worker across toggles so requests finish closing before the next one starts.
    private ScheduledThreadPoolExecutor worker;
    private Future<?> request;
    private long generation;
    private long rateLimitedUntil;
    private long displayedSecond = Long.MIN_VALUE;
    private boolean loaded;
    private boolean failed;
    private boolean started;
    private boolean active;

    public EventListFeature(ConfigCatalog configs, EventListConfig config, HudRegistry hudRegistry,
            CommandRegistry commands, Languages language) {
        this.language = language;
        this.configs = configs;
        client = configs.clientThread();
        this.config = config;
        this.commands = commands;
        this.hudRegistry = hudRegistry;
        hud = new EventListHud(config, hudRenderer, language);
        commands.register(new EventListCommand(this, language).definition(), config, false);
        languageListener = () -> {
            hud.localize();
            if (active) {
                rebuild(System.currentTimeMillis());
            }
        };
    }

    List<PitEvent> events(EventType type) {
        List<PitEvent> matching = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (PitEvent event : events) {
            if (event.type == type && event.endsAt > now) {
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
        MinecraftForge.EVENT_BUS.register(fontListener);
        language.addListener(languageListener);
        hudRegistry.register(hud);
        configs.addListener(this);
        if (config.enabled()) {
            activate();
        }
        commands.setActive(config, true);
    }

    @Override
    public void stop() {
        client.check();
        if (!started) {
            return;
        }
        started = false;
        commands.setActive(config, false);
        MinecraftForge.EVENT_BUS.unregister(fontListener);
        configs.removeListener(this);
        language.removeListener(languageListener);
        deactivate();
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
        hudRegistry.unregister(hud);
    }

    private void activate() {
        active = true;
        generation++;
        rebuild(System.currentTimeMillis());
        MinecraftForge.EVENT_BUS.register(this);
        fetch(rateLimitedUntil);
    }

    private void deactivate() {
        active = false;
        generation++;
        MinecraftForge.EVENT_BUS.unregister(this);
        if (request != null) {
            request.cancel(true);
            request = null;
        }
        events.clear();
        loaded = false;
        failed = false;
        displayedSecond = Long.MIN_VALUE;
        hud.snapshot(EventListSnapshot.build(events, config, System.currentTimeMillis(), "", true,
                language));
        hud.close();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started) {
            return;
        }
        if (changes.affects("eventlist", config.hud().translateText().id())) {
            hud.localize();
        }
        if (changes.affects("eventlist", config.endpoint.id())) {
            rateLimitedUntil = 0L;
            if (active) {
                deactivate();
            }
        }
        for (Setting<?> setting : config.settings()) {
            if (changes.affects("eventlist", setting.id())) {
                if (config.enabled() && !active) {
                    activate();
                } else if (!config.enabled() && active) {
                    deactivate();
                } else if (active) {
                    rebuild(System.currentTimeMillis());
                } else {
                    hud.close();
                }
                return;
            }
        }
    }

    private void fetch(long requestAt) {
        if (worker == null) {
            worker = new ScheduledThreadPoolExecutor(1, task -> {
                Thread thread = new Thread(task, "12pit-event-list");
                thread.setDaemon(true);
                return thread;
            });
            worker.setRemoveOnCancelPolicy(true);
        }
        long requestGeneration = generation;
        String endpoint = config.endpoint.get();
        request = worker.schedule(() -> {
            EventFeedClient.Result result = feed.fetch(endpoint);
            if (!Thread.currentThread().isInterrupted()) {
                client.execute(() -> accept(requestGeneration, result));
            }
        }, Math.max(0L, requestAt - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
    }

    private void accept(long requestGeneration, EventFeedClient.Result result) {
        if (!started || !active || generation != requestGeneration) {
            return;
        }
        request = null;
        long now = System.currentTimeMillis();
        rateLimitedUntil = Math.max(rateLimitedUntil, result.retryAt);
        failed = result.events == null;
        if (!failed) {
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
        }
        // The feed updates on the hour in UTC. Leave five minutes for it to finish.
        long interval = failed ? 300_000L : 3_600_000L;
        long offset = failed ? 0L : 300_000L;
        long earliest = Math.max(now + 1L, rateLimitedUntil);
        long nextRequestAt = (earliest - offset + interval - 1L) / interval * interval + offset;
        rebuild(now);
        fetch(nextRequestAt);
    }

    private void rebuild(long now) {
        boolean translate = config.hud().translateText().get();
        String message = failed ? language.translate("Could not load events", translate)
                : loaded ? language.translate("Event schedule has ended", translate)
                        : language.translate("Loading events…", translate);
        hud.snapshot(EventListSnapshot.build(events, config, now, message, true, language));
        displayedSecond = now / 1000L;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!active || event.type != ElementType.ALL || hudRegistry.editing()
                || minecraft.theWorld == null || !HudRenderer.visible(config.hud())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now / 1000L != displayedSecond) {
            events.removeIf(scheduled -> scheduled.endsAt <= now);
            rebuild(now);
        }
        hudRenderer.render(hud, HudRenderer.layout(hud, event.resolution.getScaledWidth(),
                event.resolution.getScaledHeight(), event.resolution.getScaleFactor(), false),
                event.partialTicks, false);
    }

    // Forge needs this listener class to be public.
    public final class FontListener {
        @SubscribeEvent
        public void onTextureStitch(TextureStitchEvent.Post event) {
            hud.close();
        }
    }
}
