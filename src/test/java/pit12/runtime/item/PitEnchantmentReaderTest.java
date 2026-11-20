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
package pit12.runtime.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

public final class PitEnchantmentReaderTest {
    @Test
    public void skipsAnInvalidEntryWithoutLosingTheValidOne() {
        ItemStack stack =
                stackWithEnchantments(0, compound("telebow", "foo"), compound("telebow", 2));
        Iterator<PitEnchantments.Entry> result = PitEnchantmentReader.read(stack).iterator();
        PitEnchantments.Entry entry = result.next();
        assertEquals("telebow", entry.getKey());
        assertEquals(2, entry.getLevel());
        assertFalse(result.hasNext());
    }

    @Test
    public void levelOfSkipsInvalidDuplicateBeforeReturningPositiveLevel() {
        ItemStack stack = stackWithEnchantments(0, compound("telebow", 0), compound("telebow", 3));
        assertEquals(3, PitEnchantmentReader.levelOf(stack, PitEnchantment.Telebow));
        assertTrue(PitEnchantmentReader.contains(stack, PitEnchantment.Telebow));
        assertEquals(0, PitEnchantmentReader.levelOf(stack, PitEnchantment.Combo_Venom));
        assertEquals(0, PitEnchantmentReader.levelOf(null, PitEnchantment.Telebow));
    }

    @Test
    public void preservesGemmedFlagEvenWhenEnchantmentListIsEmpty() {
        ItemStack stack = stackWithEnchantments(1);
        PitEnchantments result = PitEnchantmentReader.read(stack);
        assertTrue(result.gemmed());
        assertFalse(result.iterator().hasNext());
    }

    private static ItemStack stackWithEnchantments(int gemUses, NBTTagCompound... entries) {
        ItemStack stack = new ItemStack(new Item());
        NBTTagCompound extra = new NBTTagCompound();
        if (gemUses > 0) {
            extra.setInteger("UpgradeGemsUses", gemUses);
        }
        if (entries.length > 0) {
            NBTTagList enchantments = new NBTTagList();
            for (NBTTagCompound entry : entries) {
                enchantments.appendTag(entry);
            }
            extra.setTag("CustomEnchants", enchantments);
        }
        stack.setTagInfo("ExtraAttributes", extra);
        return stack;
    }

    private static NBTTagCompound compound(String key, int level) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Key", key);
        tag.setInteger("Level", level);
        return tag;
    }

    private static NBTTagCompound compound(String key, String invalidLevel) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Key", key);
        tag.setString("Level", invalidLevel);
        return tag;
    }
}
