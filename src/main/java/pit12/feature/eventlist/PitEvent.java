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

final class PitEvent {
    enum Phase {
        FUTURE, PREPARING, ACTIVE, ENDED
    }

    final EventType type;
    final long timestamp;
    final boolean major;
    final long startsAt;
    final long endsAt;

    PitEvent(EventType type, long timestamp, boolean major) {
        this.type = type;
        this.timestamp = timestamp;
        this.major = major;
        // The feed timestamps major announcements, three minutes before play begins.
        startsAt = timestamp + (major ? 180_000L : 0L);
        endsAt = startsAt + type.durationSeconds * 1000L;
    }

    Phase phase(long now) {
        if (now < timestamp) {
            return Phase.FUTURE;
        }
        if (now < startsAt) {
            return Phase.PREPARING;
        }
        return now < endsAt ? Phase.ACTIVE : Phase.ENDED;
    }
}
