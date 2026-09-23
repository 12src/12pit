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

/** All methods are client-thread confined because the source entities are client-owned. */
public final class PlayerEquipmentCache {
    public static final int HELD_ITEM = 1;
    public static final int LEGGINGS = 1 << 1;
    public static final int ALL = HELD_ITEM | LEGGINGS;
    private final Map<UUID, PlayerEquipmentSnapshot> entries =
            new LinkedHashMap<UUID, PlayerEquipmentSnapshot>();
    private final Map<UUID, Integer> dirty = new LinkedHashMap<UUID, Integer>();

    public void markDirty(UUID playerId, int slots) {
        if (playerId == null || (slots & ALL) == 0) {
            return;
        }
        Integer previous = dirty.get(playerId);
        dirty.put(playerId,
                Integer.valueOf((previous == null ? 0 : previous.intValue()) | (slots & ALL)));
    }

    /** Draining at the Tick boundary coalesces multiple packets for one player. */
    public Map<UUID, Integer> drainDirty() {
        if (dirty.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Integer> pending = new LinkedHashMap<UUID, Integer>(dirty);
        dirty.clear();
        return pending;
    }

    public PlayerEquipmentSnapshot observe(EntityPlayer player, int slots) {
        UUID playerId = player.getUniqueID();
        PlayerEquipmentSnapshot previous = entries.get(playerId);
        int knownSlots = previous == null ? 0 : knownSlots(previous);
        ItemStack heldItem = previous == null ? null : previous.copyHeldItem();
        ItemStack leggings = previous == null ? null : previous.copyLeggings();
        PitEnchantments heldEnchantments =
                previous == null ? PitEnchantments.empty() : previous.heldEnchantments();
        PitEnchantments leggingsEnchantments =
                previous == null ? PitEnchantments.empty() : previous.leggingsEnchantments();
        if ((slots & HELD_ITEM) != 0) {
            heldItem = player.getHeldItem();
            heldEnchantments = PitEnchantmentReader.read(heldItem);
            knownSlots |= HELD_ITEM;
        }
        if ((slots & LEGGINGS) != 0) {
            leggings = player.getCurrentArmor(1);
            leggingsEnchantments = PitEnchantmentReader.read(leggings);
            knownSlots |= LEGGINGS;
        }
        long revision = previous == null ? 1L : previous.revision() + 1L;
        PlayerEquipmentSnapshot snapshot = new PlayerEquipmentSnapshot(playerId, heldItem, leggings,
                heldEnchantments, leggingsEnchantments, knownSlots, revision);
        entries.put(playerId, snapshot);
        return snapshot;
    }

    public PlayerEquipmentSnapshot remove(UUID playerId) {
        if (playerId == null) {
            return null;
        }
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

    private static int knownSlots(PlayerEquipmentSnapshot snapshot) {
        int slots = 0;
        if (snapshot.heldItemKnown()) {
            slots |= HELD_ITEM;
        }
        if (snapshot.leggingsKnown()) {
            slots |= LEGGINGS;
        }
        return slots;
    }
}
