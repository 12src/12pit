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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.item.PitEnchantments;

/** Use this cache on the client thread. It reads live entities. */
public final class PlayerEquipmentCache {
    public static final int HELD_ITEM = 1;
    public static final int LEGGINGS = 1 << 1;
    public static final int BOOTS = 1 << 2;
    public static final int CHESTPLATE = 1 << 3;
    public static final int HELMET = 1 << 4;
    public static final int ALL = HELD_ITEM | LEGGINGS | BOOTS | CHESTPLATE | HELMET;
    private final Map<UUID, PlayerEquipmentSnapshot> entries = new LinkedHashMap<>();
    private final Map<UUID, Integer> dirty = new LinkedHashMap<>();

    public static int slotsFor(int equipmentSlot) {
        switch (equipmentSlot) {
            case 0:
                return HELD_ITEM;
            case 1:
                return BOOTS;
            case 2:
                return LEGGINGS;
            case 3:
                return CHESTPLATE;
            case 4:
                return HELMET;
            default:
                return 0;
        }
    }

    public void markDirty(UUID playerId, int slots) {
        dirty.merge(playerId, slots, (existing, added) -> existing | added);
    }

    /** Call once per tick to group each player's equipment changes. */
    public Map<UUID, Integer> drainDirty() {
        if (dirty.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Integer> pending = new LinkedHashMap<>(dirty);
        dirty.clear();
        return pending;
    }

    public PlayerEquipmentSnapshot observe(EntityPlayer player, int slots) {
        UUID playerId = player.getUniqueID();
        PlayerEquipmentSnapshot previous = entries.get(playerId);
        int knownSlots = previous == null ? 0 : previous.knownSlots();
        ItemStack[] equipment = previous == null ? new ItemStack[5] : previous.equipmentForUpdate();
        for (int slot = 0; slot < equipment.length; slot++) {
            if ((slots & slotsFor(slot)) != 0) {
                ItemStack stack =
                        slot == 0 ? player.getHeldItem() : player.getCurrentArmor(slot - 1);
                equipment[slot] = stack == null ? null : stack.copy();
            }
        }
        PitEnchantments heldEnchantments =
                previous == null ? PitEnchantments.empty() : previous.heldEnchantments();
        PitEnchantments leggingsEnchantments =
                previous == null ? PitEnchantments.empty() : previous.leggingsEnchantments();
        if ((slots & HELD_ITEM) != 0) {
            PitEnchantments updated = PitEnchantmentReader.read(equipment[0]);
            if (!heldEnchantments.sameContents(updated)) {
                heldEnchantments = updated;
            }
            knownSlots |= HELD_ITEM;
        }
        if ((slots & LEGGINGS) != 0) {
            PitEnchantments updated = PitEnchantmentReader.read(equipment[2]);
            if (!leggingsEnchantments.sameContents(updated)) {
                leggingsEnchantments = updated;
            }
            knownSlots |= LEGGINGS;
        }
        long revision = previous == null ? 1L : previous.revision() + 1L;
        PlayerEquipmentSnapshot snapshot = new PlayerEquipmentSnapshot(playerId, equipment,
                heldEnchantments, leggingsEnchantments, knownSlots | slots, revision);
        entries.put(playerId, snapshot);
        return snapshot;
    }

    public PlayerEquipmentSnapshot remove(UUID playerId) {
        PlayerEquipmentSnapshot removed = entries.remove(playerId);
        dirty.remove(playerId);
        return removed;
    }

    public void clear() {
        entries.clear();
        dirty.clear();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public PlayerEquipmentSnapshot loadedEquipment(UUID playerId) {
        return entries.get(playerId);
    }
}
