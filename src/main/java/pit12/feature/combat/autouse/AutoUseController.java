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

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import pit12.shared.input.MinecraftActions;

final class AutoUseController {
    private final AutoUseConfig config;
    private final MinecraftActions actions;
    private int cooldown;
    private int slotDelay;
    private int previousSlot = -1;
    private int selectedSlot;
    private Item pendingItem;
    private boolean manualInputThisTick;

    AutoUseController(AutoUseConfig config, MinecraftActions actions) {
        this.config = config;
        this.actions = actions;
    }

    void tick() {
        if (cooldown > 0)
            cooldown--;
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
        if (pendingItem != null) {
            slotDelay = 0;
            pendingItem = null;
        }
    }

    void tryUse(InventoryPlayer inventory, float health) {
        if (cooldown > 0 || manualInputThisTick)
            return;
        if (previousSlot >= 0) {
            if (pendingItem != null && slotDelay == 0)
                useSelectedItem(inventory, health);
            return;
        }
        boolean goldenHead = config.goldenHead.ready(health);
        boolean soup = config.soup.ready(health);
        boolean ragePotato = config.ragePotato.ready(health);
        boolean fracturedSoul = config.fracturedSoul.ready(health);
        if (!goldenHead && !soup && !ragePotato && !fracturedSoul)
            return;
        int heldSlot = inventory.currentItem;
        if (matches(inventory.getCurrentItem(), goldenHead, soup, ragePotato, fracturedSoul)) {
            click();
            return;
        }
        int hotbarSize = InventoryPlayer.getHotbarSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (slot == heldSlot || !matches(stack, goldenHead, soup, ragePotato, fracturedSoul))
                continue;
            previousSlot = heldSlot;
            selectedSlot = slot;
            inventory.currentItem = slot;
            slotDelay = config.switchDelay.get();
            if (slotDelay == 0)
                click();
            else
                pendingItem = stack.getItem();
            return;
        }
    }

    private void useSelectedItem(InventoryPlayer inventory, float health) {
        if (inventory.currentItem != selectedSlot) {
            manualSlotChange();
            return;
        }
        ItemStack held = inventory.getCurrentItem();
        if (!matches(held, config.goldenHead.ready(health), config.soup.ready(health),
                config.ragePotato.ready(health), config.fracturedSoul.ready(health))
                || held.getItem() != pendingItem) {
            cancelClick();
            return;
        }
        click();
    }

    private void click() {
        recordClick();
        actions.pit12$rightClick();
    }

    private void recordClick() {
        pendingItem = null;
        cooldown = config.interval.get();
        if (previousSlot >= 0)
            slotDelay = config.returnDelay.get();
    }

    void beforeActions(InventoryPlayer inventory, boolean usingItem) {
        if (previousSlot < 0)
            return;
        if (inventory.currentItem != selectedSlot) {
            manualSlotChange();
        } else if (pendingItem != null) {
            if (manualInputThisTick)
                recordClick();
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
        cooldown = 0;
        slotDelay = 0;
        previousSlot = -1;
        pendingItem = null;
        manualInputThisTick = false;
    }

    private static boolean matches(ItemStack stack, boolean goldenHead, boolean soup,
            boolean ragePotato, boolean fracturedSoul) {
        if (stack == null || stack.stackSize <= 0)
            return false;
        Item item = stack.getItem();
        return goldenHead && item == Items.skull
                || soup && (item == Items.rabbit_stew || item == Items.mushroom_stew)
                || ragePotato && item == Items.baked_potato
                || fracturedSoul && item == Items.magma_cream;
    }
}
