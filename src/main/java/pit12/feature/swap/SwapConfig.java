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
package pit12.feature.swap;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class SwapConfig extends FeatureConfig {
    final BooleanSetting rightClick;
    final BooleanSetting autoSwap;
    final ChoiceSetting inventoryDisplay;
    final IntegerSetting workspace;
    final BooleanSetting restoreWorkspace;
    final BooleanSetting preferEmpty;
    final IntegerSetting openDelay;
    final IntegerSetting swapDelay;
    final IntegerSetting bindingDelay;
    final IntegerSetting closeDelay;
    final IntegerSetting unequipKey;
    final BooleanSetting bindingMessages;
    final BooleanSetting swapMessages;
    final BooleanSetting messageDetails;
    final BooleanSetting highlight;
    final BooleanSetting venomArmor;
    final BooleanSetting venomSpade;
    final BooleanSetting skipVenomPants;
    final IntegerSetting spadeSlot;
    final BooleanSetting escapePod;
    final BooleanSetting phoenix;
    final IntegerSetting podThreshold;
    final IntegerSetting phoenixThreshold;
    final ChoiceSetting pantsPriority;
    final BooleanSetting highLives;
    final BooleanSetting restorePants;
    final ChoiceSetting pantsRestoreMode;
    final IntegerSetting pantsRestoreThreshold;

    public SwapConfig() {
        super("swap", "Swap", new ConfigCategory("player", "Player", 50),
                "Swaps armor and hotbar items manually, when poisoned or at low health.");
        subcategory("general", "General");
        rightClick = booleanSetting("right_click", "Right click swap",
                "Right-click held armor to replace the armor in its slot.", true);
        inventoryDisplay = choiceSetting("inventory_display", "Inventory visibility",
                "Shows or hides the inventory during swaps.", 0,
                new ChoiceSetting.Choice(0, "Hidden"), new ChoiceSetting.Choice(1, "Visible"));
        openDelay = integerSliderSetting("open_delay", "Open delay",
                "Ticks to wait after opening the inventory before the first click. Set to 0 for no wait.",
                2, 0, 10, 1);
        swapDelay = integerSliderSetting("swap_delay", "Swap delay",
                "Ticks between clicks for right click and automatic swaps.", 1, 0, 10, 1);
        closeDelay = integerSliderSetting("close_delay", "Close delay",
                "Ticks to wait after the last request finishes before closing the inventory.", 1, 0,
                10, 1);
        swapMessages = booleanSetting("swap_messages", "Swap messages",
                "Shows a message after items are swapped or unequipped.", true);
        workspace = integerSliderSetting("workspace_slot", "Transfer slot",
                "Hotbar slot used to move armor from the inventory.", 1, 1, 9, 1);
        restoreWorkspace = booleanSetting("restore_workspace", "Restore transfer slot",
                "Restores the transfer slot's contents after moving armor. Slot bindings take priority.",
                true);
        preferEmpty = booleanSetting("prefer_empty_hotbar", "Prefer empty hotbar slot",
                "Uses the first empty hotbar slot as the transfer slot. Otherwise uses the selected transfer slot.",
                true);
        subcategory("bindings", "Bindings");
        unequipKey = keybindSetting("unequip_all_key", "Unequip all",
                "Moves worn armor into free inventory slots.", 0);
        bindingDelay = integerSliderSetting("binding_delay", "Binding delay",
                "Ticks between clicks for bindings and unequipping.", 1, 0, 10, 1);
        bindingMessages = booleanSetting("binding_messages", "Binding messages",
                "Shows messages when bindings are added, removed or cleared.", true);
        messageDetails = booleanSetting("message_details", "Details",
                "Shows item names and enchantments in binding and swap messages.", false);
        highlight = booleanSetting("highlight_bindings", "Show binding keys",
                "Shows keys on bound items outside their target slots.", true);
        subcategory("automatic_swap", "Automatic swap");
        autoSwap = booleanSetting("auto_swap", "Automatic swap",
                "Automatically swaps items when poisoned or at low health.", false);
        subsubcategory("venom", "Venom");
        venomArmor = booleanSetting("venom_armor", "Swap diamond armor",
                "Equips diamond leggings and boots when poisoned.", false);
        venomSpade = booleanSetting("venom_spade", "Swap Combat Spade",
                "Moves a Combat Spade to the selected hotbar slot when poisoned.", false);
        skipVenomPants = booleanSetting("skip_venom_pants", "Skip while wearing Venom",
                "Skips poison swaps while wearing Combo Venom leggings.", false);
        spadeSlot = integerSliderSetting("spade_slot", "Combat Spade slot",
                "Hotbar slot for the Combat Spade.", 1, 1, 9, 1);
        subsubcategory("health", "Pod & Phoenix");
        escapePod = booleanSetting("escape_pod", "Use Escape Pod",
                "Equips Escape Pod leggings at or below their health threshold.", true);
        podThreshold = integerSliderSetting("pod_threshold", "Escape Pod threshold",
                "Health points for Escape Pod swaps. Two points equal one heart.", 6, 0, 20, 1);
        phoenix = booleanSetting("phoenix", "Use Phoenix",
                "Equips Phoenix leggings at or below their health threshold.", true);
        phoenixThreshold = integerSliderSetting("phoenix_threshold", "Phoenix threshold",
                "Health points for Phoenix swaps. Two points equal one heart.", 6, 0, 20, 1);
        pantsPriority = choiceSetting("pants_priority", "Prefer first",
                "Chooses which unused enchantment to equip when both are available and eligible.",
                0, new ChoiceSetting.Choice(0, "Escape Pod"),
                new ChoiceSetting.Choice(1, "Phoenix"));
        highLives = booleanSetting("phoenix_high_lives", "Prefer high Lives",
                "Chooses Phoenix leggings with the most remaining Lives.", true);
        restorePants = booleanSetting("restore_pants", "Restore leggings",
                "Restores the leggings worn before an automatic health swap.", true);
        pantsRestoreMode = choiceSetting("pants_restore_mode", "Restore mode",
                "Restores leggings after health recovers or the equipped enchantment triggers.", 0,
                new ChoiceSetting.Choice(0, "Health"), new ChoiceSetting.Choice(1, "Used"));
        pantsRestoreThreshold = integerSliderSetting("pants_restore_threshold", "Restore threshold",
                "Health points needed to restore leggings in Health mode. Health must also exceed the trigger threshold.",
                6, 0, 20, 1);
    }

    static final class Options {
        final boolean rightClick;
        final int unequipKey;
        final boolean visible;
        final int workspace;
        final boolean restore;
        final boolean preferEmpty;
        final int openDelay;
        final int swapDelay;
        final int bindingDelay;
        final int closeDelay;
        final boolean messages;
        final boolean details;

        Options(SwapConfig config) {
            rightClick = config.rightClick.get();
            unequipKey = config.unequipKey.get();
            visible = config.inventoryDisplay.get() == 1;
            workspace = config.workspace.get() - 1;
            restore = config.restoreWorkspace.get();
            preferEmpty = config.preferEmpty.get();
            openDelay = config.openDelay.get();
            swapDelay = config.swapDelay.get();
            bindingDelay = config.bindingDelay.get();
            closeDelay = config.closeDelay.get();
            messages = config.swapMessages.get();
            details = config.messageDetails.get();
        }
    }
}
