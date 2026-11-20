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
package pit12.feature.client.hudeditor;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class HudEditorSnapTest {
    @Test
    public void snapsWithinThreePixelsAndHoldsStickyHysteresisUntilReleased() {
        HudEditorSnap snap = new HudEditorSnap();
        snap.prepare(new HudEditorSnap.Box(50, 50, 20, 10), 200, 100,
                Collections.singletonList(new HudEditorSnap.Box(40, 10, 20, 10)));
        assertEquals(40, snap.horizontal.resolve(42, false));
        assertEquals(40, snap.horizontal.resolve(44, false));
        assertEquals(45, snap.horizontal.resolve(45, false));
        assertEquals(40, snap.horizontal.resolve(41, false));
        assertEquals(41, snap.horizontal.resolve(41, true));
    }

    @Test
    public void touchingScreenBoundaryReleasesPreviousStickySnap() {
        HudEditorSnap snap = new HudEditorSnap();
        snap.prepare(new HudEditorSnap.Box(10, 10, 20, 10), 200, 100,
                Collections.singletonList(new HudEditorSnap.Box(3, 10, 30, 10)));
        assertEquals(3, snap.horizontal.resolve(3, false));
        assertEquals(3, snap.horizontal.resolve(1, false));
        assertEquals(0, snap.horizontal.resolve(0, false));
    }

    @Test
    public void prefersScreenEdgeThenMatchingHudSideAtEqualDistance() {
        HudEditorSnap edgeVsSide = new HudEditorSnap();
        edgeVsSide.prepare(new HudEditorSnap.Box(10, 10, 20, 10), 200, 100,
                Collections.singletonList(new HudEditorSnap.Box(2, 10, 30, 10)));
        assertEquals(0, edgeVsSide.horizontal.resolve(1, false));
        HudEditorSnap matchingVsOpposite = new HudEditorSnap();
        matchingVsOpposite.prepare(new HudEditorSnap.Box(10, 10, 10, 10), 300, 100, Arrays.asList(
                new HudEditorSnap.Box(20, 10, 40, 10), new HudEditorSnap.Box(62, 10, 40, 10)));
        assertEquals(62, matchingVsOpposite.horizontal.resolve(61, false));
    }
}
