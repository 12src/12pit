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
package pit12.runtime.pit;

import java.util.Objects;

public final class PitSnapshot {
    private static final PitSnapshot INITIAL = new PitSnapshot(PitMap.UNKNOWN, 0L);
    private final PitMap map;
    private final long revision;

    public static PitSnapshot initial() {
        return INITIAL;
    }

    public PitSnapshot(PitMap map, long revision) {
        this.map = Objects.requireNonNull(map, "map");
        this.revision = revision;
    }

    public PitMap map() {
        return map;
    }

    public PitState pitState() {
        return map == PitMap.UNKNOWN ? PitState.UNKNOWN : PitState.IN_PIT;
    }

    /** Returns UNKNOWN until the map has been identified. */
    public SpawnState spawnStateAt(double x, double y, double z) {
        return map.spawnStateAt(x, y, z);
    }

    public long revision() {
        return revision;
    }
}
