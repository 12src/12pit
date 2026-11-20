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
package pit12.runtime.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PitEnchantmentsTest {
    @Test
    public void formatsColoredAndUncoloredNamesAcrossAllModes() {
        PitEnchantments enchantments = PitEnchantments.create(
                new PitEnchantments.Entry[] {
                        new PitEnchantments.Entry("regularity", PitEnchantment.Regularity, 3),
                        new PitEnchantments.Entry("evil_within", PitEnchantment.Evil_Within, 2)},
                2, true);
        assertEquals("§r§4§lREG §f§l3§7 / §r§lEvil Within §f§l2 §a♦",
                enchantments.formatBoldDisplayNames(true));
        assertEquals("§r§4REG §f3§7 / §rEvil Within §f2", enchantments.formatDisplayNames(false));
        assertEquals("§r§4§lREG§7 / §r§lEvil Within §f§l2 §a♦",
                enchantments.formatBoldDisplayNamesWithoutLevelThree(true));
        assertEquals("§r§4§lREG§7 / §r§lEvil Within",
                enchantments.formatBoldDisplayNamesWithoutLevels(false));
    }

    @Test
    public void returnsNullWhenOnlyUnknownEnchantmentsExistEvenIfGemmed() {
        PitEnchantments unknownGemmed = PitEnchantments.create(
                new PitEnchantments.Entry[] {new PitEnchantments.Entry("custom_unknown", null, 2)},
                1, true);
        assertNull(unknownGemmed.formatBoldDisplayNames(true));
        assertNull(unknownGemmed.formatDisplayNames(true));
    }

    @Test
    public void sameContentsComparesEntriesOrderAndGemmedState() {
        PitEnchantments first = PitEnchantments.create(
                new PitEnchantments.Entry[] {
                        new PitEnchantments.Entry("regularity", PitEnchantment.Regularity, 3)},
                1, false);
        PitEnchantments matching = PitEnchantments.create(
                new PitEnchantments.Entry[] {
                        new PitEnchantments.Entry("regularity", PitEnchantment.Regularity, 3)},
                1, false);
        PitEnchantments gemmed = PitEnchantments.create(
                new PitEnchantments.Entry[] {
                        new PitEnchantments.Entry("regularity", PitEnchantment.Regularity, 3)},
                1, true);
        PitEnchantments lowerLevel = PitEnchantments.create(
                new PitEnchantments.Entry[] {
                        new PitEnchantments.Entry("regularity", PitEnchantment.Regularity, 2)},
                1, false);
        assertTrue(first.sameContents(matching));
        assertFalse(first.sameContents(gemmed));
        assertFalse(first.sameContents(lowerLevel));
    }
}
