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
package pit12.feature.provider.telebow;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.util.Vec3;
import org.junit.Test;

public final class TelebowTeleportMatchTest {
    private final Vec3 landing = new Vec3(0, 0, 0);

    @Test
    public void matchesWithinTheWindowWhetherTeleportOrLandingComesFirst() {
        Vec3 destination = new Vec3(1, 0, 0);
        assertTrue(TelebowTeleportMatch.matches(landing, destination, 50, false, 50));
        assertTrue(TelebowTeleportMatch.matches(landing, destination, -50, false, 50));
        assertFalse(TelebowTeleportMatch.matches(landing, destination, -51, false, 50));
    }

    @Test
    public void enforcesThreeBlockDistanceBoundaryFromLanding() {
        assertTrue(TelebowTeleportMatch.matches(landing, new Vec3(3, 0, 0), 0, true, 50));
        assertFalse(TelebowTeleportMatch.matches(landing, new Vec3(3.1, 0, 0), 0, true, 50));
    }

    @Test
    public void zeroWindowUsesTickIdentityInsteadOfElapsedTime() {
        Vec3 destination = new Vec3(1, 0, 0);
        assertTrue(TelebowTeleportMatch.matches(landing, destination, 1, true, 0));
        assertFalse(TelebowTeleportMatch.matches(landing, destination, 0, false, 0));
    }
}
