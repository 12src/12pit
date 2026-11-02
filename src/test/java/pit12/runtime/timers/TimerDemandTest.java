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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TimerDemandTest {
    @Test
    public void unresolvedRelationsRequireEveryRemoteGroup() {
        assertFalse(TimerDemand.matches(TimerDemand.OTHER, TimerDemand.REMOTE));
        assertFalse(
                TimerDemand.matches(TimerDemand.FRIEND | TimerDemand.ENEMY, TimerDemand.REMOTE));
        assertFalse(TimerDemand.matches(TimerDemand.OWN | TimerDemand.OTHER, TimerDemand.REMOTE));
        assertTrue(TimerDemand.matches(TimerDemand.REMOTE, TimerDemand.REMOTE));
        assertTrue(TimerDemand.matches(TimerDemand.ALL, TimerDemand.REMOTE));
    }

    @Test
    public void mergedRequestsPreserveEachConsumersFilter() {
        TimerDemand requests = new TimerDemand();
        Object hud = new Object();
        Object nametags = new Object();
        int hudSelection = TimerKind.PIN.select(TimerDemand.OTHER);
        requests.set(hud, hudSelection);
        int combined = requests.set(nametags, TimerKind.PIN.select(TimerDemand.REMOTE));
        assertTrue(TimerDemand.matches(TimerKind.PIN.groups(combined), TimerDemand.REMOTE));
        assertFalse(TimerDemand.matches(TimerKind.PIN.groups(hudSelection), TimerDemand.REMOTE));
        combined = requests.set(nametags, 0);
        assertFalse(TimerDemand.matches(TimerKind.PIN.groups(combined), TimerDemand.REMOTE));
    }

    @Test
    public void resolvedRelationsUseTheirSelectedGroup() {
        assertTrue(TimerDemand.matches(TimerDemand.OTHER, TimerDemand.OTHER));
        assertFalse(TimerDemand.matches(TimerDemand.OTHER, TimerDemand.FRIEND));
        assertTrue(TimerDemand.matches(TimerDemand.FRIEND, TimerDemand.FRIEND));
        assertTrue(TimerDemand.matches(TimerDemand.OWN, TimerDemand.OWN));
        assertFalse(TimerDemand.matches(TimerDemand.REMOTE, TimerDemand.OWN));
        assertFalse(TimerDemand.matches(TimerDemand.ALL, 0));
    }
}
