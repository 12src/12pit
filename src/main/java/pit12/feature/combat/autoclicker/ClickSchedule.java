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
package pit12.feature.combat.autoclicker;

import java.util.concurrent.ThreadLocalRandom;

final class ClickSchedule {
    private long nextClickNanos;
    private boolean scheduled;

    boolean ready(long now, int firstCps, int secondCps) {
        if (scheduled && now - nextClickNanos < 0) {
            return false;
        }
        boolean due = scheduled;
        double minimumDelay = 1_000_000_000.0 / Math.max(firstCps, secondCps);
        double maximumDelay = 1_000_000_000.0 / Math.min(firstCps, secondCps);
        nextClickNanos = now + (long) (minimumDelay
                + ThreadLocalRandom.current().nextDouble() * (maximumDelay - minimumDelay));
        scheduled = true;
        return due;
    }

    void reset() {
        scheduled = false;
    }
}
