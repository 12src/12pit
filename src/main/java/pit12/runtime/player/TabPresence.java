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
package pit12.runtime.player;

import java.util.Map;
import java.util.UUID;

/** Queries and subscriptions require the client thread. */
public interface TabPresence {
    boolean contains(UUID playerId);

    /** Lists Tab entries with known names. {@link #contains(UUID)} also includes unnamed entries. */
    Map<UUID, String> players();

    void addListener(TabPresenceListener listener);

    void removeListener(TabPresenceListener listener);
}
