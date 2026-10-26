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

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import pit12.shared.nbt.NbtTypes;

public final class PitEnchantmentReader {
    private PitEnchantmentReader() {}

    public static PitEnchantments read(ItemStack stack) {
        NBTTagCompound extra = extraAttributes(stack);
        if (extra == null) {
            return PitEnchantments.empty();
        }
        NBTTagList enchantments = customEnchantments(extra);
        int count = enchantments == null ? 0 : enchantments.tagCount();
        PitEnchantments.Entry[] entries = new PitEnchantments.Entry[count];
        int size = 0;
        for (int index = 0; index < count; index++) {
            NBTTagCompound tag = enchantments.getCompoundTagAt(index);
            // NBT getters return "" or 0 for missing or mismatched tags.
            String key = tag.getString("Key");
            int level = tag.getInteger("Level");
            if (key.isEmpty() || level <= 0) {
                continue;
            }
            entries[size++] = new PitEnchantments.Entry(key, PitEnchantment.fromKey(key), level);
        }
        return PitEnchantments.create(entries, size, extra.getInteger("UpgradeGemsUses") > 0);
    }

    public static boolean contains(ItemStack stack, PitEnchantment enchantment) {
        return levelOf(stack, enchantment) > 0;
    }

    public static int levelOf(ItemStack stack, PitEnchantment enchantment) {
        NBTTagList enchantments = customEnchantments(extraAttributes(stack));
        if (enchantments == null) {
            return 0;
        }
        String expectedKey = enchantment.getKey();
        int count = enchantments.tagCount();
        for (int index = 0; index < count; index++) {
            NBTTagCompound tag = enchantments.getCompoundTagAt(index);
            if (expectedKey.equals(tag.getString("Key"))) {
                int level = tag.getInteger("Level");
                if (level > 0) {
                    return level;
                }
            }
        }
        return 0;
    }

    private static NBTTagCompound extraAttributes(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }
        NBTTagCompound itemTag = stack.getTagCompound();
        // Missing compounds and lists return empty tags, so check the keys first.
        if (!itemTag.hasKey("ExtraAttributes", NbtTypes.COMPOUND)) {
            return null;
        }
        return itemTag.getCompoundTag("ExtraAttributes");
    }

    private static NBTTagList customEnchantments(NBTTagCompound extraAttributes) {
        if (extraAttributes == null) {
            return null;
        }
        if (!extraAttributes.hasKey("CustomEnchants", NbtTypes.LIST)) {
            return null;
        }
        NBTTagList enchantments =
                extraAttributes.getTagList("CustomEnchants", NbtTypes.COMPOUND);
        return enchantments.tagCount() == 0 ? null : enchantments;
    }
}
