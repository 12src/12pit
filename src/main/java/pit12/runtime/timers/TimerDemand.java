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

import java.util.IdentityHashMap;

public final class TimerDemand {
    public static final int OWN = 1;
    public static final int FRIEND = 2;
    public static final int ENEMY = 4;
    public static final int OTHER = 8;
    public static final int REMOTE = FRIEND | ENEMY | OTHER;
    public static final int ALL = OWN | REMOTE;
    private final IdentityHashMap<Object, Integer> requests = new IdentityHashMap<>(2);

    int set(Object owner, int selection) {
        if (selection == 0)
            requests.remove(owner);
        else
            requests.put(owner, selection);
        int combined = 0;
        for (int request : requests.values())
            combined |= request;
        return combined;
    }

    void clear() {
        requests.clear();
    }

    static boolean matches(int audience, int group) {
        // An unresolved relation can belong to any remote group.
        return group != 0 && (audience & group) == group;
    }

    public static int groups(boolean own, boolean friends, boolean enemies, boolean others) {
        return (own ? OWN : 0) | (friends ? FRIEND : 0) | (enemies ? ENEMY : 0)
                | (others ? OTHER : 0);
    }
}
