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
package pit12.runtime.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HudConfigTest {
    @Test
    public void changingAnchorChoicePreservesResolvedScreenPosition() {
        StubHudConfig feature = new StubHudConfig();
        assertEquals(10, feature.hud.resolveX(200, 40));
        assertEquals(20, feature.hud.resolveY(100, 20));
        feature.anchorSetting().set(HudAnchor.BOTTOM_RIGHT.id());
        assertFalse(feature.hud.placement().automatic());
        assertEquals(HudAnchor.BOTTOM_RIGHT, feature.hud.placement().anchor());
        assertEquals(10, feature.hud.resolveX(200, 40));
        assertEquals(20, feature.hud.resolveY(100, 20));
    }

    @Test
    public void placementAppliesOffsetsDirectlyWithoutAnchorRecomputation() {
        StubHudConfig feature = new StubHudConfig();
        feature.hud.resolveX(200, 40);
        feature.hud.resolveY(100, 20);
        feature.hud.placement(new HudPlacement(HudAnchor.BOTTOM_RIGHT, -5, -6, true));
        assertTrue(feature.hud.placement().automatic());
        assertEquals(HudAnchor.BOTTOM_RIGHT, feature.hud.placement().anchor());
        assertEquals(-5, feature.hud.placement().offsetX());
        assertEquals(-6, feature.hud.placement().offsetY());
    }

    @Test
    public void fromOriginPicksNearestAnchorAndPreservesCoordinates() {
        HudPlacement topLeft = HudPlacement.fromOrigin(0, 0, 40, 20, 200, 100);
        assertEquals(HudAnchor.TOP_LEFT, topLeft.anchor());
        assertEquals(0, topLeft.offsetX());
        assertEquals(0, topLeft.offsetY());
        HudPlacement bottomRight = HudPlacement.fromOrigin(160, 80, 40, 20, 200, 100);
        assertEquals(HudAnchor.BOTTOM_RIGHT, bottomRight.anchor());
        assertEquals(0, bottomRight.offsetX());
        assertEquals(0, bottomRight.offsetY());
        HudPlacement center = HudPlacement.fromOrigin(80, 40, 40, 20, 200, 100);
        assertEquals(HudAnchor.CENTER, center.anchor());
        assertEquals(0, center.offsetX());
        assertEquals(0, center.offsetY());
    }

    private static final class StubHudConfig extends FeatureConfig {
        final HudConfig hud = hudConfig("hud", HudAnchor.TOP_LEFT, 10, 20, false);

        StubHudConfig() {
            super("foo", "Foo", ConfigCategory.RENDER, "");
        }

        ChoiceSetting anchorSetting() {
            for (Setting<?> setting : settings()) {
                if ("hud.anchor".equals(setting.id())) {
                    return (ChoiceSetting) setting;
                }
            }
            throw new AssertionError("Missing hud.anchor setting");
        }
    }
}
