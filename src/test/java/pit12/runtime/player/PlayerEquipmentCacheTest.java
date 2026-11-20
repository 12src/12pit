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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

public final class PlayerEquipmentCacheTest {
    private final PlayerEquipmentCache equipment = new PlayerEquipmentCache();

    @Test
    public void combinesChangesForTheSamePlayerUntilTheyAreDrained() {
        UUID player = new UUID(0, 1);
        equipment.markDirty(player, PlayerEquipmentCache.HELD_ITEM);
        equipment.markDirty(player, PlayerEquipmentCache.LEGGINGS);
        int pending = equipment.drainDirty().get(player);
        assertEquals(PlayerEquipmentCache.HELD_ITEM | PlayerEquipmentCache.LEGGINGS, pending);
        assertTrue(equipment.drainDirty().isEmpty());
    }

    @Test
    public void removingOrClearingDropsPendingDirtyChanges() {
        UUID first = new UUID(0, 1);
        UUID second = new UUID(0, 2);
        equipment.markDirty(first, PlayerEquipmentCache.HELD_ITEM);
        equipment.markDirty(second, PlayerEquipmentCache.BOOTS);
        equipment.remove(first);
        assertEquals(Collections.singletonMap(second, PlayerEquipmentCache.BOOTS),
                equipment.drainDirty());
        equipment.markDirty(second, PlayerEquipmentCache.HELMET);
        equipment.clear();
        assertTrue(equipment.drainDirty().isEmpty());
    }
}
