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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

final class EventFeedClient {
    static final long REFRESH_THRESHOLD = 10_800_000L;
    private static final Logger LOGGER = Logger.getLogger(EventFeedClient.class.getName());
    private static final String[] ENDPOINTS =
            {"https://raw.githubusercontent.com/BrookeAFK/brookeafk-api/main/events.js",
                    "https://fastly.jsdelivr.net/gh/BrookeAFK/brookeafk-api@main/events.js",
                    "https://gcore.jsdelivr.net/gh/BrookeAFK/brookeafk-api@main/events.js",
                    "https://testingcf.jsdelivr.net/gh/BrookeAFK/brookeafk-api@main/events.js",
                    "https://cdn.jsdelivr.net/gh/BrookeAFK/brookeafk-api@main/events.js"};
    private long loadedThrough;
    private long purgedThrough = -1L;

    Result fetch() {
        ExecutorService racers = Executors.newFixedThreadPool(ENDPOINTS.length, task -> {
            Thread thread = new Thread(task, "12pit-event-feed");
            thread.setDaemon(true);
            return thread;
        });
        try (CloseableHttpClient http = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(5000)
                        .setConnectionRequestTimeout(5000).setSocketTimeout(10000).build())
                .setMaxConnTotal(ENDPOINTS.length).setMaxConnPerRoute(ENDPOINTS.length)
                .useSystemProperties().disableAutomaticRetries().build()) {
            Result result = race(http, racers);
            if (result.events != null) {
                long through = latestTimestamp(result.events);
                loadedThrough = through;
                if (through - System.currentTimeMillis() < REFRESH_THRESHOLD
                        && through > purgedThrough) {
                    purgedThrough = through;
                    purge(http, racers);
                    Result refreshed = race(http, racers);
                    if (refreshed.events != null) {
                        loadedThrough = latestTimestamp(refreshed.events);
                        return refreshed;
                    }
                }
            }
            return result;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return new Result(null, 0L);
        } catch (IOException | ExecutionException failure) {
            LOGGER.log(Level.WARNING, "Could not load the event schedule", failure);
            return new Result(null, 0L);
        } finally {
            racers.shutdownNow();
        }
    }

    private Result race(CloseableHttpClient http, ExecutorService racers)
            throws InterruptedException, ExecutionException {
        ExecutorCompletionService<Result> completed = new ExecutorCompletionService<>(racers);
        List<HttpGet> requests = new ArrayList<>();
        List<Future<Result>> futures = new ArrayList<>();
        long retryAt = Long.MAX_VALUE;
        try {
            for (String endpoint : ENDPOINTS) {
                HttpGet request = new HttpGet(endpoint);
                requests.add(request);
                futures.add(completed.submit(() -> fetch(http, request)));
            }
            for (int i = 0; i < ENDPOINTS.length; i++) {
                Result result = completed.take().get();
                retryAt = Math.min(retryAt, result.retryAt);
                if (result.events != null) {
                    // A stale mirror must not shorten a schedule we already loaded.
                    if (latestTimestamp(result.events) >= loadedThrough) {
                        return result;
                    }
                }
            }
            LOGGER.warning("Could not load the event schedule from any endpoint");
            return new Result(null, retryAt);
        } finally {
            for (Future<Result> future : futures) {
                future.cancel(true);
            }
            for (HttpGet request : requests) {
                request.abort();
            }
        }
    }

    private void purge(CloseableHttpClient http, ExecutorService racers)
            throws InterruptedException, ExecutionException {
        HttpGet request =
                new HttpGet("https://purge.jsdelivr.net/gh/BrookeAFK/brookeafk-api@main/events.js");
        Future<?> task = racers.submit(() -> {
            try (CloseableHttpResponse response = http.execute(request)) {
                int status = response.getStatusLine().getStatusCode();
                if (status < 200 || status >= 300) {
                    LOGGER.fine("Event schedule cache purge returned HTTP " + status);
                }
            } catch (IOException failure) {
                if (!request.isAborted()) {
                    LOGGER.log(Level.FINE, "Could not purge the event schedule cache", failure);
                }
            }
        });
        try {
            task.get();
        } finally {
            task.cancel(true);
            request.abort();
        }
    }

    private long latestTimestamp(List<PitEvent> events) {
        long latest = 0L;
        for (PitEvent event : events) {
            latest = Math.max(latest, event.timestamp);
        }
        return latest;
    }

    private Result fetch(CloseableHttpClient http, HttpGet request) {
        long retryAt = 0L;
        try (CloseableHttpResponse response = http.execute(request)) {
            Header retryAfter = response.getFirstHeader("Retry-After");
            retryAt = retryAfter(retryAfter == null ? null : retryAfter.getValue(),
                    System.currentTimeMillis());
            int status = response.getStatusLine().getStatusCode();
            if (status != 200) {
                LOGGER.fine("Event schedule request to " + request.getURI() + " returned HTTP "
                        + status);
                return new Result(null, retryAt);
            }
            if (response.getEntity() == null) {
                throw new IOException("Event schedule response has no body");
            }
            StringBuilder body = new StringBuilder();
            try (InputStreamReader reader = new InputStreamReader(response.getEntity().getContent(),
                    StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("Event schedule request was canceled");
                    }
                    if (body.length() + count > 262_144) {
                        throw new IOException("Event schedule is too large");
                    }
                    body.append(buffer, 0, count);
                }
            }
            return new Result(parse(body.toString()), retryAt);
        } catch (IOException | JsonParseException | NumberFormatException
                | ArithmeticException failure) {
            if (!request.isAborted() && !Thread.currentThread().isInterrupted()) {
                LOGGER.log(Level.FINE, "Could not load the event schedule from " + request.getURI(),
                        failure);
            }
            return new Result(null, retryAt);
        }
    }

    private List<PitEvent> parse(String json) throws IOException {
        JsonElement root = new JsonParser().parse(json);
        if (!root.isJsonArray()) {
            throw new IOException("Event schedule must be a JSON array");
        }
        List<PitEvent> events = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IOException("Event schedule contains a non-object row");
            }
            JsonObject row = element.getAsJsonObject();
            JsonElement name = row.get("event");
            JsonElement timestamp = row.get("timestamp");
            JsonElement kind = row.get("type");
            if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()
                    || kind == null || !kind.isJsonPrimitive()
                    || !kind.getAsJsonPrimitive().isString() || timestamp == null
                    || !timestamp.isJsonPrimitive() || !timestamp.getAsJsonPrimitive().isNumber()) {
                throw new IOException("Event schedule row has invalid fields");
            }
            String category = kind.getAsString();
            if (!"major".equals(category) && !"minor".equals(category)) {
                throw new IOException("Event schedule row has an unknown category");
            }
            long time = timestamp.getAsBigDecimal().longValueExact();
            if (time <= 0L || time > Long.MAX_VALUE - 480_000L) {
                throw new IOException("Event schedule row has an invalid timestamp");
            }
            EventType type = EventType.fromName(name.getAsString());
            if (type != null) {
                events.add(new PitEvent(type, time, "major".equals(category)));
            }
        }
        if (events.isEmpty() && root.getAsJsonArray().size() > 0) {
            throw new IOException("Event schedule contains no supported events");
        }
        return events;
    }

    private long retryAfter(String value, long now) {
        if (value == null) {
            return 0L;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds >= 0L && seconds <= (Long.MAX_VALUE - now) / 1000L) {
                return now + seconds * 1000L;
            }
            LOGGER.fine("Ignoring an invalid Retry-After delay");
        } catch (NumberFormatException failure) {
            try {
                return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
                        .toEpochMilli();
            } catch (DateTimeParseException invalidDate) {
                LOGGER.fine("Ignoring an invalid Retry-After date");
            }
        }
        return 0L;
    }

    static final class Result {
        final List<PitEvent> events;
        final long retryAt;

        // The worker gives up ownership of events when returning this result.
        Result(List<PitEvent> events, long retryAt) {
            this.events = events;
            this.retryAt = retryAt;
        }
    }
}
