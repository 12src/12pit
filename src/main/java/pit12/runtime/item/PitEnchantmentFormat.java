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

import pit12.runtime.config.ChoiceSetting;

public final class PitEnchantmentFormat {
    public static final int BOLD_LEVELS = 0;
    public static final int PLAIN_LEVELS = 1;
    public static final int HIDE_LEVEL_THREE = 2;
    public static final int NAMES_ONLY = 3;

    private PitEnchantmentFormat() {}

    public static ChoiceSetting.Choice[] choices() {
        return new ChoiceSetting.Choice[] {
                new ChoiceSetting.Choice(BOLD_LEVELS, "§4§lREG §f§l3§7 / §6§lABS §f§l2"),
                new ChoiceSetting.Choice(PLAIN_LEVELS, "§4REG §f3§7 / §6ABS §f2"),
                new ChoiceSetting.Choice(HIDE_LEVEL_THREE, "§4§lREG§7 / §6§lABS §f§l2"),
                new ChoiceSetting.Choice(NAMES_ONLY, "§4§lREG§7 / §6§lABS")};
    }

    /** Returns null when there are no known enchantments to show. */
    public static String format(PitEnchantments enchantments, int format) {
        switch (format) {
            case PLAIN_LEVELS:
                return enchantments.formatDisplayNames();
            case HIDE_LEVEL_THREE:
                return enchantments.formatBoldDisplayNamesWithoutLevelThree();
            case NAMES_ONLY:
                return enchantments.formatBoldDisplayNamesWithoutLevels();
            case BOLD_LEVELS:
                return enchantments.formatBoldDisplayNames();
            default:
                throw new IllegalStateException("Unknown enchantment format: " + format);
        }
    }
}
