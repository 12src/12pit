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
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

final class EventFeedClient {
    private static final Logger LOGGER = Logger.getLogger(EventFeedClient.class.getName());

    Result fetch() {
        HttpURLConnection connection = null;
        long retryAt = 0L;
        try {
            connection = (HttpURLConnection) new URL(
                    "https://api.github.com/repos/BrookeAFK/brookeafk-api/contents/events.js?ref=main")
                    .openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/vnd.github.raw+json");
            connection.setRequestProperty("User-Agent", "12pit");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            int status = connection.getResponseCode();
            long now = System.currentTimeMillis();
            retryAt = retryAfter(connection.getHeaderField("Retry-After"), now);
            if ("0".equals(connection.getHeaderField("X-RateLimit-Remaining"))) {
                String reset = connection.getHeaderField("X-RateLimit-Reset");
                if (reset != null) {
                    try {
                        long seconds = Long.parseLong(reset);
                        if (seconds > 0L && seconds <= (Long.MAX_VALUE - 1000L) / 1000L) {
                            retryAt = Math.max(retryAt, seconds * 1000L + 1000L);
                        }
                    } catch (NumberFormatException failure) {
                        LOGGER.fine("Ignoring an invalid GitHub rate limit reset time");
                    }
                }
            }
            if (status != HttpURLConnection.HTTP_OK) {
                LOGGER.warning("Event schedule request returned HTTP " + status);
                return new Result(null, retryAt);
            }
            StringBuilder response = new StringBuilder();
            try (InputStreamReader reader =
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("Event schedule request was canceled");
                    }
                    if (response.length() + count > 262_144) {
                        throw new IOException("Event schedule is too large");
                    }
                    response.append(buffer, 0, count);
                }
            }
            return new Result(parse(response.toString()), retryAt);
        } catch (IOException | RuntimeException failure) {
            if (!Thread.currentThread().isInterrupted()) {
                LOGGER.log(Level.WARNING, "Could not load the event schedule", failure);
            }
            return new Result(null, retryAt);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
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

        // The worker transfers its parsed rows to the client through this result.
        Result(List<PitEvent> events, long retryAt) {
            this.events = events == null ? null : Collections.unmodifiableList(events);
            this.retryAt = retryAt;
        }
    }
}
