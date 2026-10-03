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
package pit12.feature.itemesp;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ColorSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;

public final class ItemEspConfig extends FeatureConfig {
    private final BooleanSetting showRaffle;
    private final BooleanSetting showGold;
    private final ColorSetting raffleColor;
    private final ColorSetting goldColor;

    public ItemEspConfig() {
        super("itemesp", "Item ESP", new ConfigCategory("render", "Render", 100),
                "Shows dropped raffle tickets and gold ingots through blocks.", true, false);
        subcategory("raffle_tickets", "Raffle tickets");
        showRaffle = booleanSetting("show_raffle", "Show raffle tickets",
                "Marks dropped name tags.", true);
        raffleColor = colorPickerSetting("raffle_color", "Raffle ticket color", "", 0x80FF8000);
        subcategory("gold_ingots", "Gold ingots");
        showGold =
                booleanSetting("show_gold", "Show gold ingots", "Marks dropped gold ingots.", true);
        goldColor = colorPickerSetting("gold_color", "Gold ingot color", "", 0x80FFD700);
    }

    public boolean hasTargets() {
        return showRaffle() || showGold();
    }

    public boolean showRaffle() {
        return showRaffle.get();
    }

    public boolean showGold() {
        return showGold.get();
    }

    public int raffleColor() {
        return raffleColor.get();
    }

    public int goldColor() {
        return goldColor.get();
    }
}
