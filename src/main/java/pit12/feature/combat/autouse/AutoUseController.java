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
package pit12.feature.combat.autouse;

import java.util.Arrays;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import pit12.feature.combat.autouse.AutoUseConfig.ItemSettings;
import pit12.feature.combat.autouse.AutoUseConfig.UseItem;
import pit12.shared.input.MinecraftActions;

final class AutoUseController {
    private static final UseItem[] ITEMS = UseItem.values();
    private final AutoUseConfig config;
    private final MinecraftActions actions;
    private final long[] nextUseTick = new long[ITEMS.length];
    private long tick;
    private int slotDelay;
    private int previousSlot = -1;
    private int selectedSlot;
    private String pendingItemName;
    private boolean manualInputThisTick;

    AutoUseController(AutoUseConfig config, MinecraftActions actions) {
        this.config = config;
        this.actions = actions;
    }

    void tick() {
        tick++;
        if (slotDelay > 0)
            slotDelay--;
        manualInputThisTick = false;
    }

    void manualClick() {
        manualInputThisTick = true;
    }

    void manualSlotChange() {
        manualInputThisTick = true;
        cancelClick();
        previousSlot = -1;
    }

    void cancelClick() {
        if (pendingItemName != null) {
            slotDelay = 0;
            pendingItemName = null;
        }
    }

    void tryUse(InventoryPlayer inventory, float health, PullOperation pull) {
        if (manualInputThisTick)
            return;
        if (previousSlot >= 0) {
            if (pendingItemName != null && slotDelay == 0)
                useSelectedItem(inventory, health);
            return;
        }
        int ready = 0;
        for (UseItem item : ITEMS)
            if (ready(item, health))
                ready |= 1 << item.ordinal();
        if (ready == 0)
            return;
        int heldSlot = inventory.currentItem;
        UseItem held = itemForName(displayName(inventory.getCurrentItem()));
        if (includes(ready, held)) {
            click(held);
            return;
        }
        int hotbarSize = InventoryPlayer.getHotbarSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            if (slot == heldSlot)
                continue;
            String name = displayName(inventory.getStackInSlot(slot));
            UseItem item = itemForName(name);
            if (!includes(ready, item))
                continue;
            previousSlot = heldSlot;
            selectedSlot = slot;
            inventory.currentItem = slot;
            slotDelay = config.items.get(item).switchDelay.get();
            if (slotDelay == 0)
                click(item);
            else
                pendingItemName = name;
            return;
        }
        if (pull != null) {
            int inventoryReady = 0;
            for (UseItem item : ITEMS)
                if (includes(ready, item) && config.items.get(item).fromInventory.get())
                    inventoryReady |= 1 << item.ordinal();
            if (inventoryReady == 0)
                return;
            int targetSlot = transferSlot(inventory, hotbarSize);
            if (targetSlot < 0)
                return;
            UseItem wanted = null;
            int largest = 0;
            int source = -1;
            for (int slot = hotbarSize; slot < inventory.mainInventory.length; slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                UseItem item = itemForName(displayName(stack));
                if (wanted == null && includes(inventoryReady, item))
                    wanted = item;
                if (wanted != null && item == wanted && stack.stackSize > largest) {
                    source = slot;
                    largest = stack.stackSize;
                }
            }
            if (source >= 0)
                pull.move(source, targetSlot);
        }
    }

    boolean readyFromInventory(ItemStack stack, float health) {
        UseItem item = itemForName(displayName(stack));
        return item != null && config.items.get(item).fromInventory.get() && ready(item, health);
    }

    private boolean ready(UseItem item, float health) {
        return tick >= nextUseTick[item.ordinal()] && config.items.get(item).ready(health);
    }

    private int transferSlot(InventoryPlayer inventory, int hotbarSize) {
        int heldSlot = inventory.currentItem;
        for (int slot = 0; slot < hotbarSize; slot++)
            if (slot != heldSlot && inventory.getStackInSlot(slot) == null)
                return slot;
        if (inventory.getCurrentItem() == null)
            return heldSlot;
        return config.replaceWhenFull.get() ? config.replacementSlot.get() - 1 : -1;
    }

    private void useSelectedItem(InventoryPlayer inventory, float health) {
        if (inventory.currentItem != selectedSlot) {
            manualSlotChange();
            return;
        }
        String name = displayName(inventory.getCurrentItem());
        if (!pendingItemName.equals(name)) {
            cancelClick();
            return;
        }
        UseItem item = itemForName(name);
        if (config.items.get(item).ready(health))
            click(item);
        else
            cancelClick();
    }

    private void click(UseItem item) {
        recordClick(item);
        actions.pit12$rightClick();
    }

    private void recordClick(UseItem item) {
        pendingItemName = null;
        ItemSettings timing = config.items.get(item);
        nextUseTick[item.ordinal()] = tick + timing.interval.get();
        if (previousSlot >= 0)
            slotDelay = timing.returnDelay.get();
    }

    void beforeActions(InventoryPlayer inventory, boolean usingItem) {
        if (previousSlot < 0)
            return;
        if (inventory.currentItem != selectedSlot) {
            manualSlotChange();
        } else if (pendingItemName != null) {
            if (manualInputThisTick)
                recordClick(itemForName(pendingItemName));
        } else if (slotDelay == 0 && !usingItem) {
            inventory.currentItem = previousSlot;
            previousSlot = -1;
        }
    }

    void stop(InventoryPlayer inventory, boolean usingItem) {
        cancelClick();
        slotDelay = 0;
        beforeActions(inventory, usingItem);
    }

    void reset() {
        Arrays.fill(nextUseTick, 0);
        tick = 0;
        slotDelay = 0;
        previousSlot = -1;
        pendingItemName = null;
        manualInputThisTick = false;
    }

    private static boolean includes(int ready, UseItem item) {
        return item != null && (ready & (1 << item.ordinal())) != 0;
    }

    private static String displayName(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.hasDisplayName()
                ? stack.getDisplayName()
                : null;
    }

    private static UseItem itemForName(String name) {
        if (name == null)
            return null;
        switch (name) {
            case "§6Golden Head":
                return UseItem.GOLDEN_HEAD;
            case "§aTasty Soup":
            case "§aAssist Soup":
                return UseItem.SOUP;
            case "§cRage Potato":
                return UseItem.RAGE_POTATO;
            case "§bFractured Soul":
                return UseItem.FRACTURED_SOUL;
            case "§6AAA-Rated Steak":
                return UseItem.AAA_RATED_STEAK;
            case "§aAura of Protection":
                return UseItem.AURA_OF_PROTECTION;
            case "§cFirst-Aid Egg":
                return UseItem.FIRST_AID_EGG;
            default:
                return null;
        }
    }

    interface PullOperation {
        void move(int sourceSlot, int hotbarSlot);
    }
}
