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
package pit12.feature.armorswap;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class AutoArmorSwapConfig extends FeatureConfig {
    private final IntegerSetting swapDelay;
    private final IntegerSetting closeDelay;
    private final BooleanSetting transparent;
    private final BooleanSetting hideCursor;
    private final BooleanSetting showSwapMessages;

    public AutoArmorSwapConfig() {
        super("auto_armor_swap", "Auto Armor Swap", new ConfigCategory("misc", "Misc", 75),
                "Swaps the held armor piece with the worn piece when right-clicking, by briefly opening "
                        + "the inventory and sending the number-key swap click a player would make.");
        swapDelay = integerSliderSetting("swap_delay", "Swap delay",
                "Ticks between opening the inventory and the swap click.", 0, 0, 10, 1);
        closeDelay = integerSliderSetting("close_delay", "Close delay",
                "Ticks between the swap click and closing the inventory.", 1, 0, 10, 1);
        transparent = booleanSetting("transparent", "Transparent",
                "Renders the simulated inventory invisibly.", true);
        hideCursor = booleanSetting("hide_cursor", "Hide cursor",
                "Keeps the crosshair instead of a mouse pointer while the inventory is open.",
                true);
        showSwapMessages = booleanSetting("show_swap_messages", "Swap messages",
                "Shows the equipped armor in chat after the swap.", true);
    }

    public int swapDelay() {
        return swapDelay.get().intValue();
    }

    public int closeDelay() {
        return closeDelay.get().intValue();
    }

    public boolean transparent() {
        return transparent.get().booleanValue();
    }

    public boolean hideCursor() {
        return hideCursor.get().booleanValue();
    }

    public boolean showSwapMessages() {
        return showSwapMessages.get().booleanValue();
    }
}
