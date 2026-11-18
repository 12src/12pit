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
package pit12.runtime.timers;

import net.minecraft.util.Vec3;

public final class TelebowTeleportMatch {
    private TelebowTeleportMatch() {}

    public static long predictionWindowNanos(TelebowSettings settings, boolean local) {
        return settings.predictionMargin() * (local ? 2_000_000L : 1_000_000L);
    }

    public static long windowNanos(TelebowSettings settings, boolean local) {
        return predictionWindowNanos(settings, local) + settings.teleportExtraMargin() * 1_000_000L;
    }

    public static boolean matches(Vec3 landing, Vec3 destination, long timeDifference,
            boolean sameTick, long windowNanos) {
        return (windowNanos == 0 ? sameTick : Math.abs(timeDifference) <= windowNanos)
                && landing.squareDistanceTo(destination) <= 9;
    }
}
