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
package pit12.feature.client.denick;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

public final class DenickQueryTest {
    @Test
    public void rejectsAmbiguousOwners() {
        DenickQuery query = new DenickQuery();
        Map<String, String> owners = new LinkedHashMap<>();
        owners.put("a1", "[120] §6[MVP++] Foo");
        owners.put("b2", "[120] §c[YOUTUBE] Bar");
        assertFalse(query.selectOwner(owners, null, null));
    }

    @Test
    public void rejectsOwnersWithoutANickableRank() {
        DenickQuery query = new DenickQuery();
        Map<String, String> owners = Collections.singletonMap("a1", "[80] §a[VIP] Foo");
        assertFalse(query.selectOwner(owners, null, null));
    }

    @Test
    public void selectsTheOnlyEligibleOwnerAndStripsTheRankTag() {
        DenickQuery query = new DenickQuery();
        Map<String, String> owners = new LinkedHashMap<>();
        owners.put("a1", "[80] §a[VIP] Foo");
        owners.put("b2", "[120] §c[YOUTUBE] Bar");
        boolean matched = query.selectOwner(Collections.unmodifiableMap(owners), null, null);
        assertTrue(matched);
        assertEquals("b2", query.owner);
        assertEquals("[120] §cBar", query.displayName);
    }

    @Test
    public void reusesTheCachedDisplayNameForTheSameOwner() {
        DenickQuery query = new DenickQuery();
        Map<String, String> owners = Collections.singletonMap("a1", "[120] §6[MVP++] Foo");
        query.selectOwner(owners, "a1", "[125] §6Foo");
        assertEquals("[125] §6Foo", query.displayName);
    }

    @Test
    public void ignoresTheCachedDisplayNameForADifferentOwner() {
        DenickQuery query = new DenickQuery();
        Map<String, String> owners = Collections.singletonMap("b2", "[120] §6[MVP++] Bar");
        query.selectOwner(owners, "a1", "[125] §6Foo");
        assertEquals("[120] §6Bar", query.displayName);
    }

    @Test
    public void throwsWhenCancelledBeforeLookup() {
        ItemStack stack = new ItemStack(new Item());
        NBTTagCompound extra = new NBTTagCompound();
        extra.setLong("Nonce", 10);
        stack.setTagInfo("ExtraAttributes", extra);
        DenickItem item = DenickItem.read(stack);
        assertNotNull(item);
        assertThrows(InterruptedException.class, () -> DenickQuery.run(new PitPalClient(),
                Collections.singletonList(item), null, null, () -> true));
    }
}
