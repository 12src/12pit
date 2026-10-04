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
package pit12.feature.playerlist;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class PlayerListSnapshot {
    private static final PlayerListSnapshot EMPTY =
            new PlayerListSnapshot(Collections.<PlayerListGroup, List<PlayerListEntry>>emptyMap());
    private final Map<PlayerListGroup, List<PlayerListEntry>> entries;

    private PlayerListSnapshot(Map<PlayerListGroup, List<PlayerListEntry>> entries) {
        this.entries = entries;
    }

    public static PlayerListSnapshot empty() {
        return EMPTY;
    }

    /** The snapshot takes ownership of the map and its lists. */
    static PlayerListSnapshot create(Map<PlayerListGroup, List<PlayerListEntry>> entries) {
        return entries.isEmpty() ? EMPTY : new PlayerListSnapshot(entries);
    }

    public Map<PlayerListGroup, List<PlayerListEntry>> entries() {
        return entries;
    }

    public List<PlayerListEntry> entries(PlayerListGroup group) {
        List<PlayerListEntry> groupEntries = entries.get(group);
        return groupEntries == null ? Collections.<PlayerListEntry>emptyList() : groupEntries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
