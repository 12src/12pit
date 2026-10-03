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

import java.util.HashMap;
import java.util.Map;

/** A single scheduled event from the community events API. */
public final class PitEvent {
    // Display colors per event name, matched by substring, mirroring the in-game event colors.
    private static final Map<String, Integer> EVENT_COLORS = new HashMap<String, Integer>();
    private static final int DEFAULT_COLOR = 0xFFFFFF;
    static {
        EVENT_COLORS.put("Blockhead", 0xFFAA00);
        EVENT_COLORS.put("Pizza", 0xFF5555);
        EVENT_COLORS.put("Beast", 0x55FF55);
        EVENT_COLORS.put("Robbery", 0xFFAA00);
        EVENT_COLORS.put("Spire", 0xAA00AA);
        EVENT_COLORS.put("Squads", 0x55FFFF);
        EVENT_COLORS.put("Team Deathmatch", 0xAA00AA);
        EVENT_COLORS.put("Raffle", 0xFFAA00);
        EVENT_COLORS.put("Rage Pit", 0xFF5555);
        EVENT_COLORS.put("2x Rewards", 0x00AA00);
        EVENT_COLORS.put("Giant Cake", 0xFF55FF);
        EVENT_COLORS.put("KOTL", 0x55FF55);
        EVENT_COLORS.put("Dragon Egg", 0xAA00AA);
        EVENT_COLORS.put("Auction", 0xFFFF55);
        EVENT_COLORS.put("Quick Maths", 0xAA00AA);
        EVENT_COLORS.put("KOTH", 0x55FFFF);
        EVENT_COLORS.put("Care Package", 0xFFAA00);
        EVENT_COLORS.put("All bounty", 0xFFAA00);
    }
    private final String name;
    private final long timestamp;
    private final String type;
    private Integer cachedColor;

    public PitEvent(String name, long timestamp, String type) {
        this.name = name;
        this.timestamp = timestamp;
        this.type = type;
    }

    /** Display name, for example "2x Rewards" or "KOTH". */
    public String name() {
        return name;
    }

    /** Scheduled start in epoch milliseconds. */
    public long timestamp() {
        return timestamp;
    }

    /** Raw type from the API: "major", "minor" or something new. */
    public String type() {
        return type;
    }

    public boolean isMajor() {
        return "major".equals(type);
    }

    public long secondsUntil() {
        return (timestamp - System.currentTimeMillis()) / 1000;
    }

    public boolean isExpired() {
        return secondsUntil() < 0;
    }

    /** True while the countdown shows one minute or less. */
    public boolean isUpcoming() {
        long seconds = secondsUntil();
        return seconds > 0 && seconds <= 60;
    }

    /** Major events run a zero-to-three-minute warning phase after the countdown reaches zero. */
    public boolean isInWarningPeriod() {
        if (!isMajor()) {
            return false;
        }
        long secondsSince = -secondsUntil();
        return secondsSince >= 0 && secondsSince < 180;
    }

    /** True while the event is officially running, according to its estimated duration. */
    public boolean isActive() {
        long secondsSince = -secondsUntil();
        if (secondsSince < 0) {
            return false;
        }
        if (isMajor()) {
            return secondsSince >= 180 && secondsSince <= 480;
        }
        return secondsSince <= estimatedDuration();
    }

    public int color() {
        Integer cached = cachedColor;
        if (cached != null) {
            return cached.intValue();
        }
        int color = DEFAULT_COLOR;
        for (Map.Entry<String, Integer> candidate : EVENT_COLORS.entrySet()) {
            if (name.contains(candidate.getKey())) {
                color = candidate.getValue().intValue();
                break;
            }
        }
        cachedColor = Integer.valueOf(color);
        return color;
    }

    /** Countdown formatted as zero-padded "45s", "04m 32s" or "01h 05m". */
    public String formattedTimeUntil() {
        long seconds = secondsUntil();
        if (seconds < 0) {
            if (isActive()) {
                return "Started";
            }
            if (isInWarningPeriod()) {
                return "Starting";
            }
            return "Expired";
        }
        if (seconds < 60) {
            return twoDigits(seconds) + "s";
        }
        long minutes = seconds / 60;
        long secondsRemainder = seconds % 60;
        if (minutes < 60) {
            return twoDigits(minutes) + "m"
                    + (secondsRemainder > 0 ? " " + twoDigits(secondsRemainder) + "s" : "");
        }
        long hours = minutes / 60;
        long minutesRemainder = minutes % 60;
        return twoDigits(hours) + "h"
                + (minutesRemainder > 0 ? " " + twoDigits(minutesRemainder) + "m" : "");
    }

    private int estimatedDuration() {
        if (isMajor()) {
            if (name.contains("Robbery") || name.contains("Rage Pit")) {
                return 240;
            }
            return 300;
        }
        if (name.contains("KOTH") || name.contains("2x Rewards")) {
            return 240;
        }
        if (name.contains("KOTL")) {
            return 180;
        }
        if (name.contains("Giant Cake")) {
            return 120;
        }
        return 180;
    }

    private static String twoDigits(long value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PitEvent)) {
            return false;
        }
        PitEvent event = (PitEvent) other;
        return timestamp == event.timestamp && name.equals(event.name) && type.equals(event.type);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        return 31 * result + type.hashCode();
    }

    @Override
    public String toString() {
        return "PitEvent{name=" + name + ", timestamp=" + timestamp + ", type=" + type + "}";
    }
}
