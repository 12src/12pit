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
package pit12.feature.denick;

import java.util.regex.Pattern;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import pit12.shared.nbt.NbtTypes;

final class DenickItem {
    private static final Pattern ENCHANTMENT_KEY = Pattern.compile("[a-z_]+");
    final long nonce;
    final String query;

    private DenickItem(long nonce, String query) {
        this.nonce = nonce;
        this.query = query;
    }

    static DenickItem read(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (!root.hasKey("ExtraAttributes", NbtTypes.COMPOUND)) {
            return null;
        }
        NBTTagCompound extra = root.getCompoundTag("ExtraAttributes");
        if (!extra.hasKey("Nonce", NbtTypes.ANY_NUMERIC)) {
            return null;
        }
        long nonce = extra.getLong("Nonce");
        if (nonce >= 10) {
            return new DenickItem(nonce, "nonce" + nonce);
        }
        String type = stack.getItem() == Items.leather_leggings ? "pants1"
                : stack.getItem() == Items.golden_sword ? "swords1"
                        : stack.getItem() == Items.bow ? "bows1" : null;
        if (type == null || !extra.hasKey("MaxLives", NbtTypes.ANY_NUMERIC)) {
            return null;
        }
        int maxLives = extra.getInteger("MaxLives");
        if (maxLives <= 0) {
            return null;
        }
        StringBuilder query = new StringBuilder();
        NBTTagList enchantments = extra.getTagList("CustomEnchants", NbtTypes.COMPOUND);
        if (enchantments.tagCount() == 0) {
            return null;
        }
        for (int index = 0; index < enchantments.tagCount(); index++) {
            NBTTagCompound enchantment = enchantments.getCompoundTagAt(index);
            String key = enchantment.getString("Key");
            int level = enchantment.getInteger("Level");
            if (!ENCHANTMENT_KEY.matcher(key).matches() || level <= 0) {
                return null;
            }
            if (index > 0) {
                query.append(',');
            }
            query.append(key).append(level);
        }
        query.append(",maxlives").append(maxLives).append(',').append(type)
                .append(extra.getInteger("UpgradeGemsUses") > 0 ? ",gemmed1" : ",ungemmed1")
                .append(",plain1,order1");
        return new DenickItem(nonce, query.toString());
    }
}
