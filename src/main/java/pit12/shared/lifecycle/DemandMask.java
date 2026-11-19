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
package pit12.shared.lifecycle;

import java.util.IdentityHashMap;

public final class DemandMask {
    private final IdentityHashMap<Object, Integer> requests = new IdentityHashMap<>(2);

    public int set(Object owner, int selection) {
        if (selection == 0)
            requests.remove(owner);
        else
            requests.put(owner, selection);
        int combined = 0;
        for (int request : requests.values())
            combined |= request;
        return combined;
    }

    public void clear() {
        requests.clear();
    }
}
