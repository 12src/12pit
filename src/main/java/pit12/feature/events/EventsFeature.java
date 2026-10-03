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
package pit12.feature.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.hud.HudRenderer;
import pit12.shared.lifecycle.ClientLifecycle;

/**
 * Keeps the upcoming Pit event schedule up to date and renders it as a HUD. raw.githubusercontent.com carries the
 * freshest data but is unreachable from some networks, so it is retried with a short timeout and jsDelivr CDN mirrors
 * of the same file serve as fallbacks.
 */
public final class EventsFeature implements ClientLifecycle {
    private static final Logger LOGGER = Logger.getLogger(EventsFeature.class.getName());
    private static final String[] ENDPOINTS =
            {"https://raw.githubusercontent.com/BrookeAFK/brookeafk-api/main/events.js",
                    "https://fastly.jsdelivr.net/gh/BrookeAFK/brookeafk-api@main/events.js",
                    "https://gcore.jsdelivr.net/gh/BrookeAFK/brookeafk-api@main/events.js",
                    "https://testingcf.jsdelivr.net/gh/BrookeAFK/brookeafk-api@main/events.js",};
    private static final int RAW_RETRIES = 3;
    private static final int RAW_TIMEOUT_MS = 2000;
    private static final int ENDPOINT_TIMEOUT_MS = 5000;
    private static final long RETRY_PAUSE_MS = 200;
    private static final long REFRESH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    // The API only reports upcoming events, so a shrinking cache means the schedule ran out.
    private static final int MIN_CACHED_EVENTS = 24;
    private static final long SMART_REFRESH_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(15);
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, EventsFeature::thread);
    private final EventsConfig config;
    private final ConfigCatalog catalog;
    private final HudRegistry hudRegistry;
    private final HudRenderer hudRenderer = new HudRenderer();
    private final EventListHud hud;
    private final ConfigChangeListener configListener = ignored -> applyEnabledState();
    private final AtomicBoolean fetching = new AtomicBoolean();
    private volatile List<PitEvent> events = Collections.emptyList();
    private volatile long lastSmartRefresh;
    private volatile long lastSuccessfulUpdate;
    private volatile String lastError;
    private volatile boolean started;
    private volatile boolean running;
    // Confined to the client thread, together with the catalog's change notifications.
    private ScheduledFuture<?> refreshTask;

    private static Thread thread(Runnable task) {
        Thread worker = new Thread(task, "12pit-events-api");
        worker.setDaemon(true);
        return worker;
    }

    public EventsFeature(EventsConfig config, ConfigCatalog catalog, HudRegistry hudRegistry) {
        this.config = config;
        this.catalog = catalog;
        this.hudRegistry = hudRegistry;
        hud = new EventListHud(config, this);
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        hudRegistry.register(hud);
        catalog.addListener(configListener);
        applyEnabledState();
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        catalog.removeListener(configListener);
        setRunning(false);
        hudRegistry.unregister(hud);
    }

    /** Snapshot of the schedule, sorted by start time; safe to read from any thread. */
    public List<PitEvent> events() {
        List<PitEvent> current = events;
        if (running && current.size() <= MIN_CACHED_EVENTS
                && System.currentTimeMillis() - lastSmartRefresh > SMART_REFRESH_COOLDOWN_MS) {
            lastSmartRefresh = System.currentTimeMillis();
            scheduler.submit(this::fetchAsync);
        }
        return current;
    }

    public long lastSuccessfulUpdate() {
        return lastSuccessfulUpdate;
    }

    public String lastError() {
        return lastError;
    }

    public boolean refreshNow() {
        if (!running || !fetching.compareAndSet(false, true)) {
            return false;
        }
        scheduler.submit(this::fetchOnce);
        return true;
    }

    private void applyEnabledState() {
        setRunning(config.enabled());
    }

    private void setRunning(boolean enabled) {
        if (enabled == running) {
            return;
        }
        running = enabled;
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        if (enabled) {
            scheduler.submit(this::fetchAsync);
            refreshTask = scheduler.scheduleAtFixedRate(this::fetchAsync, REFRESH_INTERVAL_MS,
                    REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
            MinecraftForge.EVENT_BUS.register(this);
        } else {
            MinecraftForge.EVENT_BUS.unregister(this);
            events = Collections.emptyList();
            lastError = null;
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != ElementType.ALL || hudRegistry.editing() || events.isEmpty()) {
            return;
        }
        ScaledResolution resolution = event.resolution;
        hudRenderer.render(
                hud, HudRenderer.layout(hud, resolution.getScaledWidth(),
                        resolution.getScaledHeight(), resolution.getScaleFactor(), false),
                event.partialTicks, false);
    }

    private void fetchAsync() {
        if (!fetching.compareAndSet(false, true)) {
            return;
        }
        fetchOnce();
    }

    private void fetchOnce() {
        try {
            List<PitEvent> fetched = fetchFromApis();
            if (running) {
                events = fetched;
                lastSuccessfulUpdate = System.currentTimeMillis();
                lastError = null;
            }
        } catch (IOException failure) {
            lastError = failure.getMessage();
            LOGGER.log(Level.INFO, "Events refresh failed", failure);
        } finally {
            fetching.set(false);
        }
    }

    private List<PitEvent> fetchFromApis() throws IOException {
        IOException lastFailure = null;
        for (int attempt = 0; attempt < RAW_RETRIES; attempt++) {
            try {
                List<PitEvent> fetched = fetchFromEndpoint(ENDPOINTS[0], RAW_TIMEOUT_MS);
                if (!fetched.isEmpty()) {
                    return fetched;
                }
            } catch (IOException failure) {
                lastFailure = failure;
            }
            pauseBeforeRetry();
        }
        for (int index = 1; index < ENDPOINTS.length; index++) {
            try {
                List<PitEvent> fetched = fetchFromEndpoint(ENDPOINTS[index], ENDPOINT_TIMEOUT_MS);
                if (!fetched.isEmpty()) {
                    return fetched;
                }
            } catch (IOException failure) {
                lastFailure = failure;
            }
        }
        throw new IOException("All events API endpoints failed", lastFailure);
    }

    private static void pauseBeforeRetry() {
        try {
            Thread.sleep(RETRY_PAUSE_MS);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<PitEvent> fetchFromEndpoint(String endpoint, int timeoutMs)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IOException("Events API returned HTTP " + status);
            }
            try (InputStreamReader reader =
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                return parseEvents(new JsonParser().parse(reader));
            }
        } catch (RuntimeException failure) {
            throw new IOException("Events API response could not be read", failure);
        } finally {
            connection.disconnect();
        }
    }

    private static List<PitEvent> parseEvents(JsonElement parsed) throws IOException {
        if (!parsed.isJsonArray()) {
            throw new IOException("Events API returned invalid JSON");
        }
        List<PitEvent> events = new ArrayList<PitEvent>();
        for (JsonElement element : parsed.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IOException("Events API entry is not an object");
            }
            JsonObject object = element.getAsJsonObject();
            JsonElement name = object.get("event");
            JsonElement timestamp = object.get("timestamp");
            JsonElement type = object.get("type");
            if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()
                    || name.getAsString().isEmpty() || timestamp == null
                    || !timestamp.isJsonPrimitive() || !timestamp.getAsJsonPrimitive().isNumber()
                    || type == null || !type.isJsonPrimitive()
                    || !type.getAsJsonPrimitive().isString()) {
                throw new IOException("Events API entry is missing required fields");
            }
            events.add(new PitEvent(name.getAsString(), timestamp.getAsLong(), type.getAsString()));
        }
        events.sort(Comparator.comparingLong(PitEvent::timestamp));
        return Collections.unmodifiableList(events);
    }
}
