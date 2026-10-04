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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class EventListSnapshot {
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
    final List<Row> rows;
    final String dayName;
    final String dayTime;
    final String message;

    private EventListSnapshot(List<Row> rows, String dayName, String dayTime, String message) {
        this.rows = Collections.unmodifiableList(rows);
        this.dayName = dayName;
        this.dayTime = dayTime;
        this.message = message;
    }

    static EventListSnapshot build(List<PitEvent> events, EventListConfig config, long now,
            String emptyMessage, boolean filtering) {
        List<Row> rows = new ArrayList<>();
        boolean hasEvents = false;
        for (PitEvent event : events) {
            PitEvent.Phase phase = event.phase(now);
            if (phase == PitEvent.Phase.ENDED) {
                continue;
            }
            hasEvents = true;
            if (filtering && !config.shows(event)) {
                continue;
            }
            long deadline = phase == PitEvent.Phase.FUTURE ? event.timestamp
                    : phase == PitEvent.Phase.PREPARING ? event.startsAt : event.endsAt;
            String time =
                    formatTime(deadline, now, config.timeFormat(), config.zeroPadding(), phase);
            rows.add(new Row(event.type, phase, event.major, time));
            if (rows.size() == config.eventCount()) {
                break;
            }
        }
        // Matches the tracker's ten-second offset for the Pit day cycle.
        long cycle = Math.floorMod(now / 1000L + 10L, 2160L);
        boolean day = cycle < 1440L;
        return new EventListSnapshot(rows, config.showDayNight() ? (day ? "Day" : "Night") : "",
                config.showDayNight()
                        ? formatDuration((day ? 1440L : 2160L) - cycle, config.zeroPadding())
                        : "",
                rows.isEmpty() ? (hasEvents ? "No events match the filters" : emptyMessage) : "");
    }

    static EventListSnapshot sample(EventListConfig config) {
        long now = System.currentTimeMillis();
        return build(
                Arrays.asList(new PitEvent(EventType.BLOCKHEAD, now - 240_000L, true),
                        new PitEvent(EventType.KOTH, now - 30_000L, false),
                        new PitEvent(EventType.PIZZA, now - 20_000L, true),
                        new PitEvent(EventType.DRAGON_EGG, now + 150_000L, false),
                        new PitEvent(EventType.SPIRE, now + 540_000L, true),
                        new PitEvent(EventType.DOUBLE_REWARDS, now + 780_000L, false)),
                config, now, "", false);
    }

    private static String formatTime(long deadline, long now, int format, boolean padding,
            PitEvent.Phase phase) {
        if (format == 1) {
            return CLOCK.format(Instant.ofEpochMilli(deadline));
        }
        String relative = phase == PitEvent.Phase.PREPARING ? "Preparing"
                : phase == PitEvent.Phase.ACTIVE ? "Active"
                        : formatDuration(Math.max(0L, deadline - now + 999L) / 1000L, padding);
        return format == 2 ? relative + " (" + CLOCK.format(Instant.ofEpochMilli(deadline)) + ")"
                : relative;
    }

    private static String formatDuration(long seconds, boolean padding) {
        long hours = seconds / 3600L;
        long minutes = seconds / 60L % 60L;
        String tail = number(seconds % 60L, padding) + "s";
        if (hours > 0L) {
            return number(hours, padding) + "h " + number(minutes, padding) + "m " + tail;
        }
        return padding || minutes > 0L ? number(minutes, padding) + "m " + tail : tail;
    }

    private static String number(long value, boolean padding) {
        return padding && value < 10L ? "0" + value : Long.toString(value);
    }

    static final class Row {
        final EventType type;
        final PitEvent.Phase phase;
        final boolean major;
        final String time;

        private Row(EventType type, PitEvent.Phase phase, boolean major, String time) {
            this.type = type;
            this.phase = phase;
            this.major = major;
            this.time = time;
        }
    }
}
