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
package pit12.feature.provider.timertracking.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import pit12.feature.client.relation.api.PlayerAudience;

public final class TimerKindTest {
    @Test
    public void encodesAndExtractsAudienceMasksIndependentlyPerTimerKind() {
        int selection = TimerKind.PIN.select(PlayerAudience.OWN)
                | TimerKind.VENOM_WEARER.select(PlayerAudience.FRIEND)
                | TimerKind.VENOM_TARGET.select(PlayerAudience.ENEMY)
                | TimerKind.JUMP_BOOST.select(PlayerAudience.REMOTE);
        assertEquals(PlayerAudience.OWN, TimerKind.PIN.groups(selection));
        assertEquals(PlayerAudience.FRIEND, TimerKind.VENOM_WEARER.groups(selection));
        assertEquals(PlayerAudience.ENEMY, TimerKind.VENOM_TARGET.groups(selection));
        assertEquals(PlayerAudience.REMOTE, TimerKind.JUMP_BOOST.groups(selection));
    }
}
