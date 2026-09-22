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
package pit12.runtime.player;

import java.util.UUID;
import net.minecraft.item.ItemStack;
import pit12.runtime.item.PitEnchantments;

public final class PlayerEquipmentSnapshot {
    private final UUID playerId;
    private final ItemStack heldItem;
    private final ItemStack leggings;
    private final PitEnchantments heldEnchantments;
    private final PitEnchantments leggingsEnchantments;
    private final int knownSlots;
    private final long revision;

    PlayerEquipmentSnapshot(UUID playerId, ItemStack heldItem, ItemStack leggings,
            PitEnchantments heldEnchantments, PitEnchantments leggingsEnchantments, int knownSlots,
            long revision) {
        this.playerId = playerId;
        this.heldItem = copy(heldItem);
        this.leggings = copy(leggings);
        this.heldEnchantments = heldEnchantments;
        this.leggingsEnchantments = leggingsEnchantments;
        this.knownSlots = knownSlots;
        this.revision = revision;
    }

    public UUID playerId() {
        return playerId;
    }

    public long revision() {
        return revision;
    }

    public boolean heldItemKnown() {
        return (knownSlots & PlayerEquipmentCache.HELD_ITEM) != 0;
    }

    public boolean leggingsKnown() {
        return (knownSlots & PlayerEquipmentCache.LEGGINGS) != 0;
    }

    /** Returns a defensive copy, or null when the known slot is empty or unknown. */
    public ItemStack copyHeldItem() {
        return copy(heldItem);
    }

    /** Returns a defensive copy, or null when the known slot is empty or unknown. */
    public ItemStack copyLeggings() {
        return copy(leggings);
    }

    public PitEnchantments heldEnchantments() {
        return heldEnchantments;
    }

    public PitEnchantments leggingsEnchantments() {
        return leggingsEnchantments;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }
}
