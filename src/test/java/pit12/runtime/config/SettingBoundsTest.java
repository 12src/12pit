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
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class SettingBoundsTest {
    @Test
    public void snapsDecimalValuesToStepAndRoundsPrecision() {
        DoubleSetting setting = new DoubleSetting("ratio", "Ratio", "", 0.0, 0.0, 1.0, 0.25);
        setting.set(0.37);
        assertEquals(0.25, setting.get(), 0.0);
        setting.set(0.38);
        assertEquals(0.5, setting.get(), 0.0);
        assertEquals(2, setting.decimalPlaces());
    }

    @Test
    public void reachesMaximumEvenWhenRangeIsNotDivisibleByStep() {
        IntegerSetting setting = new IntegerSetting("count", "Count", "", 0, 0, 10, 4);
        setting.set(8);
        assertEquals(8, (int) setting.get());
        setting.set(10);
        assertEquals(10, (int) setting.get());
        assertThrows(IllegalArgumentException.class, () -> setting.set(-1));
        assertThrows(IllegalArgumentException.class, () -> setting.set(11));
    }

    @Test
    public void colorSettingAcceptsSigned32BitArgbAndRejectsFractionsOrOverflow() {
        ColorSetting color = new ColorSetting("tint", "Tint", "", 0);
        color.set(0xFF112233);
        assertEquals(0xFF112233, (int) color.get());
        assertEquals(0xFF112233, (int) color.requireValue((double) 0xFF112233));
        assertThrows(IllegalArgumentException.class, () -> color.requireValue(1.5));
        assertThrows(IllegalArgumentException.class,
                () -> color.requireValue(Integer.MAX_VALUE + 1L));
        assertThrows(IllegalArgumentException.class, () -> color.requireValue("ff112233"));
    }
}
