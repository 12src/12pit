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

import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import pit12.runtime.item.PitEnchantmentReader;

final class SwapBinding {
    final int key;
    final ItemIdentity identity;
    final boolean equipment;
    final int target;
    final String name;
    final String details;

    SwapBinding(int key, ItemIdentity identity, boolean equipment, int target, String name,
            String details) {
        if (key <= 0 || key >= Keyboard.KEYBOARD_SIZE || Keyboard.getKeyName(key) == null
                || target < 1 || target > (equipment ? 4 : 9) || name.isEmpty()) {
            throw new IllegalArgumentException("Invalid swap binding");
        }
        this.key = key;
        this.identity = identity;
        this.equipment = equipment;
        this.target = target;
        this.name = name;
        this.details = details;
    }

    static SwapBinding create(int key, ItemStack stack, int hotbarTarget) {
        if (stack == null)
            throw new IllegalArgumentException("Hold an item to bind");
        int armor = armorTarget(stack);
        if (hotbarTarget == 0 && armor == 0) {
            throw new IllegalArgumentException("Hold armor or specify a hotbar slot from 1 to 9");
        }
        String details = PitEnchantmentReader.read(stack).formatDisplayNames();
        ItemIdentity identity = ItemIdentity.read(stack);
        if (identity == null)
            throw new IllegalArgumentException("Held item could not be identified");
        String name = stack.getDisplayName();
        return new SwapBinding(key, identity, hotbarTarget == 0,
                hotbarTarget == 0 ? armor : hotbarTarget, name.isEmpty() ? identity.item : name,
                details == null ? "" : details);
    }

    static int armorTarget(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemArmor
                ? 4 - ((ItemArmor) stack.getItem()).armorType
                : 0;
    }

    int guiTarget() {
        return equipment ? 9 - target : 35 + target;
    }

    String display(boolean detailed) {
        return detailed && !details.isEmpty() ? name + " (" + details + "\u00a7r)" : name;
    }

    String targetName() {
        if (!equipment)
            return "Hotbar " + target;
        switch (target) {
            case 1:
                return "Boots";
            case 2:
                return "Leggings";
            case 3:
                return "Chestplate";
            default:
                return "Helmet";
        }
    }
}
