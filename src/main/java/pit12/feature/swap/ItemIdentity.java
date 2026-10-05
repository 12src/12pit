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

import static pit12.runtime.languages.Languages.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.item.PitEnchantments;

final class ItemIdentity {
    enum Kind {
        NONCE, ENCHANTS, NAME
    }

    final String item;
    final int variant;
    final Kind kind;
    final String value;

    ItemIdentity(String item, int variant, Kind kind, String value) {
        this.item = item;
        this.variant = variant;
        this.kind = kind;
        this.value = value;
        if (item.isEmpty() || kind != Kind.NAME && value.isEmpty() || variant < 0
                || kind == Kind.NONCE && Long.parseLong(value) < 10) {
            throw new IllegalArgumentException(source("Invalid item identity"));
        }
    }

    static ItemIdentity read(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0)
            return null;
        ResourceLocation registered = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registered == null)
            return null;
        String item = registered.toString();
        int variant = stack.getItem().getHasSubtypes() ? stack.getMetadata() : 0;
        if (variant < 0)
            return null;
        NBTTagCompound root = stack.getTagCompound();
        if (root != null && root.hasKey("ExtraAttributes", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound extra = root.getCompoundTag("ExtraAttributes");
            // For rage, drak, aqua...
            if (extra.hasKey("Nonce", Constants.NBT.TAG_ANY_NUMERIC)) {
                long nonce = extra.getLong("Nonce");
                if (nonce >= 10)
                    return new ItemIdentity(item, variant, Kind.NONCE, Long.toString(nonce));
            }
        }
        List<String> enchantments = new ArrayList<>();
        for (PitEnchantments.Entry entry : PitEnchantmentReader.read(stack)) {
            enchantments.add(entry.getKey() + ":" + entry.getLevel());
        }
        if (!enchantments.isEmpty()) {
            Collections.sort(enchantments);
            JsonArray signature = new JsonArray();
            for (String enchantment : enchantments)
                signature.add(new JsonPrimitive(enchantment));
            return new ItemIdentity(item, variant, Kind.ENCHANTS, signature.toString());
        }
        return new ItemIdentity(item, variant, Kind.NAME, stack.getDisplayName());
    }

    boolean matches(ItemStack stack) {
        return equals(read(stack));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ItemIdentity))
            return false;
        ItemIdentity identity = (ItemIdentity) other;
        return variant == identity.variant && kind == identity.kind && item.equals(identity.item)
                && value.equals(identity.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, variant, kind, value);
    }
}
