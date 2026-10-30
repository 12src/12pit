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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/** Only the event list worker uses this client. */
final class EventFeedClient {
    private static final Logger LOGGER = Logger.getLogger(EventFeedClient.class.getName());
    private static final String MAINLAND_ENDPOINT = "https://events.askll.dev/";
    private String defaultEndpoint =
            usesMainlandSettings() ? MAINLAND_ENDPOINT : "https://events.12pit.workers.dev/";

    Result fetch(String endpoint) {
        endpoint = endpoint.trim();
        boolean defaultService = endpoint.isEmpty();
        if (defaultService) {
            endpoint = defaultEndpoint;
        }
        long retryAt = 0L;
        try (CloseableHttpClient http = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(5000)
                        .setConnectionRequestTimeout(5000).setSocketTimeout(10000).build())
                .useSystemProperties().disableAutomaticRetries().build()) {
            while (true) {
                try (CloseableHttpResponse response = http.execute(new HttpGet(endpoint))) {
                    Header retryAfter = response.getFirstHeader("Retry-After");
                    retryAt = Math.max(retryAt,
                            retryAfter(retryAfter == null ? null : retryAfter.getValue(),
                                    System.currentTimeMillis()));
                    int status = response.getStatusLine().getStatusCode();
                    if (status != 200) {
                        LOGGER.fine("Event schedule request to " + endpoint + " returned HTTP "
                                + status);
                        return new Result(null, retryAt);
                    }
                    if (response.getEntity() == null) {
                        throw new IOException("Event schedule response has no body");
                    }
                    StringBuilder body = new StringBuilder();
                    try (InputStreamReader reader = new InputStreamReader(
                            response.getEntity().getContent(), StandardCharsets.UTF_8)) {
                        char[] buffer = new char[4096];
                        int count;
                        while ((count = reader.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) {
                                throw new InterruptedIOException(
                                        "Event schedule request was canceled");
                            }
                            if (body.length() + count > 262_144) {
                                throw new IOException("Event schedule is too large");
                            }
                            body.append(buffer, 0, count);
                        }
                    }
                    List<PitEvent> events = parse(body.toString());
                    if (defaultService) {
                        defaultEndpoint = endpoint;
                    }
                    return new Result(events, retryAt);
                } catch (ConnectTimeoutException | SocketTimeoutException | UnknownHostException
                        | SocketException | SSLException failure) {
                    if (!defaultService || MAINLAND_ENDPOINT.equals(endpoint)
                            || Thread.currentThread().isInterrupted()) {
                        throw failure;
                    }
                    LOGGER.log(Level.FINE,
                            "Event schedule request failed, trying " + MAINLAND_ENDPOINT, failure);
                    endpoint = MAINLAND_ENDPOINT;
                }
            }
        } catch (IOException | JsonParseException | IllegalArgumentException | ArithmeticException
                | DateTimeParseException failure) {
            if (!Thread.currentThread().isInterrupted()) {
                LOGGER.log(Level.WARNING, "Could not load the event schedule", failure);
            }
            return new Result(null, retryAt);
        }
    }

    private static boolean usesMainlandSettings() {
        if ("CN".equals(Locale.getDefault(Locale.Category.FORMAT).getCountry())) {
            return true;
        }
        switch (TimeZone.getDefault().getID()) {
            case "Asia/Shanghai":
            case "Asia/Urumqi":
            case "Asia/Chongqing":
            case "Asia/Chungking":
            case "Asia/Harbin":
            case "Asia/Kashgar":
            case "PRC":
            case "CTT":
                return true;
            default:
                return false;
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
            JsonElement name = row.get("name");
            JsonElement timestamp = row.get("time");
            JsonElement kind = row.get("type");
            if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()
                    || kind == null || !kind.isJsonPrimitive()
                    || !kind.getAsJsonPrimitive().isString() || timestamp == null
                    || !timestamp.isJsonPrimitive() || !timestamp.getAsJsonPrimitive().isString()) {
                throw new IOException("Event schedule row has invalid fields");
            }
            String category = kind.getAsString();
            if (!"major".equals(category) && !"minor".equals(category)) {
                throw new IOException("Event schedule row has an unknown category");
            }
            boolean major = "major".equals(category);
            long time = OffsetDateTime.parse(timestamp.getAsString()).toInstant().toEpochMilli();
            if (time <= 0L || time > Long.MAX_VALUE - 480_000L) {
                throw new IOException("Event schedule row has an invalid timestamp");
            }
            EventType type = EventType.fromName(name.getAsString());
            if (type != null) {
                events.add(new PitEvent(type, time, major));
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

        // The worker is done with this list when it returns the result.
        Result(List<PitEvent> events, long retryAt) {
            this.events = events;
            this.retryAt = retryAt;
        }
    }
}
