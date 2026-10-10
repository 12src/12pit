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

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class SwapConfig extends FeatureConfig {
    final BooleanSetting rightClick;
    final BooleanSetting randomClickTiming;
    final BooleanSetting shiftClick;
    final BooleanSetting autoSwap;
    final BooleanSetting autoSwapMessages;
    final ChoiceSetting inventoryDisplay;
    final IntegerSetting workspace;
    final BooleanSetting restoreWorkspace;
    final BooleanSetting preferEmpty;
    final IntegerSetting openDelay;
    final IntegerSetting swapDelay;
    final IntegerSetting bindingDelay;
    final IntegerSetting closeDelay;
    final BooleanSetting resumeInputNextTick;
    final IntegerSetting unequipKey;
    final BooleanSetting bindingMessages;
    final BooleanSetting swapMessages;
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
    final BooleanSetting dark;
    final ChoiceSetting darkTriggerMode;
    final IntegerSetting darkThreshold;
    final IntegerSetting darkRange;
    final BooleanSetting restoreDark;
    final IntegerSetting darkRestoreThreshold;
    final BooleanSetting skipLowPod;
    final BooleanSetting darkEnemiesOnly;
    final BooleanSetting darkIgnoreFriends;
    final ChoiceSetting darkPriority;

    public SwapConfig() {
        super("swap", source("Swap"), new ConfigCategory("combat", source("Combat"), 25), source(
                "Swaps armor and hotbar items manually, when poisoned or when players have low health."));
        subcategory("general", source("General"));
        rightClick = booleanSetting("right_click", source("Right click swap"),
                source("Right-click held armor to replace the armor in its slot."), true);
        randomClickTiming = booleanSetting("random_click_timing", source("Random click timing"),
                source("Uses a random input phase before player updates for all swaps and unequipping."),
                true);
        shiftClick = booleanSetting("shift_click", source("Shift click swap"),
                source("Use shift clicks instead of number keys to swap armor."), false);
        inventoryDisplay = choiceSetting("inventory_display", source("Inventory visibility"),
                source("Shows or hides the inventory during swaps."), 0,
                new ChoiceSetting.Choice(0, source("Hidden")),
                new ChoiceSetting.Choice(1, source("Visible")));
        openDelay = integerSliderSetting("open_delay", source("Open delay"), source(
                "Ticks to wait after opening the inventory before the first click. Set to 0 for no wait."),
                2, 0, 10, 1);
        swapDelay = integerSliderSetting("swap_delay", source("Swap delay"),
                source("Ticks between clicks for right click and automatic swaps."), 1, 0, 10, 1);
        closeDelay = integerSliderSetting("close_delay", source("Close delay"), source(
                "Ticks to wait after the last request finishes before closing the inventory."), 2,
                0, 10, 1);
        resumeInputNextTick = booleanSetting("resume_input_next_tick",
                source("Resume input next tick"),
                source("Keeps all input locked until the next tick after closing the inventory."),
                true);
        swapMessages = booleanSetting("swap_messages", source("Swap messages"),
                source("Shows a message after items are swapped or unequipped."), true);
        workspace = integerSliderSetting("workspace_slot", source("Transfer slot"),
                source("Hotbar slot used to move armor from the inventory."), 1, 1, 9, 1);
        restoreWorkspace = booleanSetting("restore_workspace", source("Restore transfer slot"),
                source("Restores the transfer slot's contents after moving armor. Slot bindings take priority."),
                true);
        preferEmpty = booleanSetting("prefer_empty_hotbar", source("Prefer empty hotbar slot"),
                source("Uses the first empty hotbar slot as the transfer slot. Otherwise uses the selected transfer slot."),
                true);
        subcategory("bindings", source("Bindings"));
        unequipKey = keybindSetting("unequip_all_key", source("Unequip all"),
                source("Moves worn armor into free inventory slots."), 0);
        bindingDelay = integerSliderSetting("binding_delay", source("Binding delay"),
                source("Ticks between clicks for bindings and unequipping."), 1, 0, 10, 1);
        bindingMessages = booleanSetting("binding_messages", source("Binding messages"),
                source("Shows messages when bindings are added, removed or cleared."), true);
        highlight = booleanSetting("highlight_bindings", source("Show binding keys"),
                source("Shows keys on bound items outside their target slots."), true);
        subcategory("automatic_swap", source("Automatic swap"));
        autoSwap = booleanSetting("auto_swap", source("Automatic swap"), source(
                "Automatically swaps items when poisoned or when you or a dark target have low health."),
                false);
        autoSwapMessages = booleanSetting("auto_swap_messages", source("Automatic swap messages"),
                source("Shows why automatic swaps happened."), true);
        subsubcategory("venom", "Venom");
        venomArmor = booleanSetting("venom_armor", source("Swap diamond armor"),
                source("Equips diamond leggings and boots when poisoned."), true);
        venomSpade = booleanSetting("venom_spade", source("Swap Combat Spade"),
                source("Moves a Combat Spade to the selected hotbar slot when poisoned."), true);
        skipVenomPants = booleanSetting("skip_venom_pants", source("Skip while wearing Venom"),
                source("Skips poison swaps while wearing Combo Venom leggings."), false);
        spadeSlot = integerSliderSetting("spade_slot", source("Combat Spade slot"),
                source("Hotbar slot for the Combat Spade."), 1, 1, 9, 1);
        subsubcategory("health", "Pod & Phoenix");
        escapePod = booleanSetting("escape_pod", source("Use Escape Pod"),
                source("Equips Escape Pod leggings at or below their health threshold."), true);
        podThreshold = integerSliderSetting("pod_threshold", source("Escape Pod threshold"),
                source("Health points for Escape Pod swaps. Two points equal one heart."), 6, 0, 20,
                1);
        phoenix = booleanSetting("phoenix", source("Use Phoenix"),
                source("Equips Phoenix leggings at or below their health threshold."), true);
        phoenixThreshold = integerSliderSetting("phoenix_threshold", source("Phoenix threshold"),
                source("Health points for Phoenix swaps. Two points equal one heart."), 6, 0, 20,
                1);
        pantsPriority = choiceSetting("pants_priority", source("Prefer first"), source(
                "Chooses which unused enchantment to equip when both are available and eligible."),
                0, new ChoiceSetting.Choice(0, "Escape Pod"),
                new ChoiceSetting.Choice(1, "Phoenix"));
        highLives = booleanSetting("phoenix_high_lives", source("Prefer high Lives"),
                source("Chooses Phoenix leggings with the most remaining Lives."), true);
        restorePants = booleanSetting("restore_pants", source("Restore leggings"),
                source("Restores the leggings worn before an automatic health swap."), true);
        pantsRestoreMode = choiceSetting("pants_restore_mode", source("Restore mode"), source(
                "Restores leggings after health recovers or the equipped enchantment triggers."), 0,
                new ChoiceSetting.Choice(0, source("Health")),
                new ChoiceSetting.Choice(1, source("Used")));
        pantsRestoreThreshold = integerSliderSetting("pants_restore_threshold",
                source("Restore threshold"),
                source("Health points needed to restore leggings in Health mode. Health must also exceed the trigger threshold."),
                6, 0, 20, 1);
        subsubcategory("dark", source("Dark"));
        dark = booleanSetting("use_dark", source("Use dark"), source(
                "Equips Somber leggings against players with low health wearing Escape Pod or Phoenix."),
                false);
        darkTriggerMode = choiceSetting("dark_trigger_mode", source("Trigger mode"), source(
                "Checks the last player you attacked or all nearby players within range. Friend and enemy filters apply to both modes."),
                0, new ChoiceSetting.Choice(0, source("Last attacked target")),
                new ChoiceSetting.Choice(1, source("Nearby players")));
        darkThreshold = integerSliderSetting("dark_threshold", source("Target health threshold"),
                source("Equips dark when a target's health is at or below this value. Uses entity health without absorption."),
                10, 0, 20, 1);
        darkRange = integerSliderSetting("dark_range", source("Target range"),
                source("Maximum straight-line distance to targets in both trigger modes."), 6, 1,
                32, 1);
        restoreDark = booleanSetting("restore_dark", source("Restore leggings"), source(
                "Restores the leggings worn before the automatic swap when no targets in the selected mode need dark."),
                true);
        darkRestoreThreshold = integerSliderSetting("dark_restore_threshold",
                source("Restore threshold"),
                source("Stops tracking a recovered target at or above this value. Health must also exceed the equip threshold."),
                10, 0, 20, 1);
        skipLowPod = booleanSetting("dark_skip_low_pod",
                source("Skip Escape Pod players below 2 hearts"),
                source("Skips Escape Pod targets below 2 hearts in both trigger modes. Phoenix still qualifies."),
                false);
        darkEnemiesOnly = booleanSetting("dark_enemies_only", source("Enemies only"),
                source("Only checks players in your enemy list."), false);
        darkIgnoreFriends = booleanSetting("dark_ignore_friends", source("Ignore friends"), source(
                "Skips friends in both trigger modes. Attacking a friend keeps the previous target."),
                true);
        darkPriority = choiceSetting("dark_priority", source("Swap priority"), source(
                "Chooses between dark and your own Escape Pod or Phoenix when both are needed. Poison swaps take priority."),
                0, new ChoiceSetting.Choice(0, source("Pod/Phoenix first")),
                new ChoiceSetting.Choice(1, source("Dark first")));
    }

    static final class Options {
        final boolean rightClick;
        final boolean randomClickTiming;
        final boolean shiftClick;
        final int unequipKey;
        final boolean visible;
        final int workspace;
        final boolean restore;
        final boolean preferEmpty;
        final int openDelay;
        final int swapDelay;
        final int bindingDelay;
        final int closeDelay;
        final boolean resumeInputNextTick;
        final boolean messages;
        final boolean automaticMessages;

        Options(SwapConfig config) {
            rightClick = config.rightClick.get();
            randomClickTiming = config.randomClickTiming.get();
            shiftClick = config.shiftClick.get();
            unequipKey = config.unequipKey.get();
            visible = config.inventoryDisplay.get() == 1;
            workspace = config.workspace.get() - 1;
            restore = config.restoreWorkspace.get();
            preferEmpty = config.preferEmpty.get();
            openDelay = config.openDelay.get();
            swapDelay = config.swapDelay.get();
            bindingDelay = config.bindingDelay.get();
            closeDelay = config.closeDelay.get();
            resumeInputNextTick = config.resumeInputNextTick.get();
            messages = config.swapMessages.get();
            automaticMessages = config.autoSwapMessages.get();
        }
    }
}
