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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PlayerListSnapshot {
    private static final PlayerListSnapshot EMPTY = new PlayerListSnapshot(
            Collections.<PlayerListGroup, List<PlayerListEntry>>emptyMap(), 0);
    private final Map<PlayerListGroup, List<PlayerListEntry>> entries;
    private final int playerCount;

    private PlayerListSnapshot(Map<PlayerListGroup, List<PlayerListEntry>> entries,
            int playerCount) {
        this.entries = entries;
        this.playerCount = playerCount;
    }

    public static PlayerListSnapshot empty() {
        return EMPTY;
    }

    static PlayerListSnapshot create(Map<PlayerListGroup, List<PlayerListEntry>> entries,
            int playerCount) {
        EnumMap<PlayerListGroup, List<PlayerListEntry>> copy =
                new EnumMap<PlayerListGroup, List<PlayerListEntry>>(PlayerListGroup.class);
        for (Map.Entry<PlayerListGroup, List<PlayerListEntry>> entry : entries.entrySet()) {
            copy.put(entry.getKey(),
                    Collections.unmodifiableList(new ArrayList<PlayerListEntry>(entry.getValue())));
        }
        return copy.isEmpty() ? EMPTY
                : new PlayerListSnapshot(Collections.unmodifiableMap(copy), playerCount);
    }

    public Map<PlayerListGroup, List<PlayerListEntry>> entries() {
        return entries;
    }

    public List<PlayerListEntry> entries(PlayerListGroup group) {
        List<PlayerListEntry> groupEntries = entries.get(group);
        return groupEntries == null ? Collections.<PlayerListEntry>emptyList() : groupEntries;
    }

    public int playerCount() {
        return playerCount;
    }

    public boolean isEmpty() {
        return playerCount == 0;
    }
}
