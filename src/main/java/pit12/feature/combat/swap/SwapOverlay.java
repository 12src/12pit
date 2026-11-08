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
package pit12.feature.combat.swap;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase.NBTPrimitive;
import pit12.shared.rendering.FontWidths;

final class SwapOverlay {
    private final Minecraft minecraft;
    private final BindingBook bindings;
    private final IdentityHashMap<ItemStack, Label> labels = new IdentityHashMap<>();
    private final IdentityHashMap<ItemStack, CachedIdentity> cache = new IdentityHashMap<>();
    private final IdentityHashMap<ItemStack, ItemIdentity> identities = new IdentityHashMap<>();

    SwapOverlay(Minecraft minecraft, BindingBook bindings) {
        this.minecraft = minecraft;
        this.bindings = bindings;
    }

    void clear() {
        labels.clear();
        cache.clear();
        identities.clear();
    }

    void refresh() {
        labels.clear();
        identities.clear();
        if (bindings.readinessProblem() != null || bindings.count() == 0
                || minecraft.thePlayer == null) {
            cache.clear();
            return;
        }
        boolean containerScreen = minecraft.currentScreen instanceof GuiContainer;
        List<Slot> inventorySlots = minecraft.thePlayer.inventoryContainer.inventorySlots;
        for (int index = 0, size = inventorySlots.size(); index < size; index++) {
            Slot slot = inventorySlots.get(index);
            if (containerScreen || slot.slotNumber >= 36
                    || slot.slotNumber >= 5 && slot.slotNumber <= 8)
                addIdentity(slot.getStack());
        }
        if (containerScreen) {
            List<Slot> containerSlots = minecraft.thePlayer.openContainer.inventorySlots;
            for (int index = 0, size = containerSlots.size(); index < size; index++) {
                addIdentity(containerSlots.get(index).getStack());
            }
        }
        cache.keySet().retainAll(identities.keySet());
        for (Map.Entry<ItemStack, ItemIdentity> entry : identities.entrySet()) {
            SwapBinding binding = bindings.matching(entry.getValue());
            if (binding == null)
                continue;
            ItemStack target =
                    minecraft.thePlayer.inventoryContainer.getSlot(binding.guiTarget()).getStack();
            if (binding.identity.equals(identities.get(target)))
                continue;
            CachedIdentity cached = cache.get(entry.getKey());
            FontRenderer font = minecraft.fontRendererObj;
            Label label = cached.label;
            if (label == null || label.binding != binding || label.font != font) {
                label = new Label(binding, font);
                cached.label = label;
            }
            labels.put(entry.getKey(), label);
        }
    }

    private void addIdentity(ItemStack stack) {
        if (stack == null || identities.containsKey(stack))
            return;
        CachedIdentity cached = cache.get(stack);
        if (cached == null || !cached.matches(stack)) {
            cached = new CachedIdentity(stack);
            cache.put(stack, cached);
        }
        identities.put(stack, cached.identity);
    }

    void draw(ItemStack stack, int x, int y) {
        Label label = labels.get(stack);
        if (label == null)
            return;
        boolean unicode = label.font.getUnicodeFlag();
        label.font.setUnicodeFlag(true);
        try {
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.disableBlend();
            label.font.drawStringWithShadow(label.text,
                    label.binding.equipment ? x + 16 - label.width : x + 2,
                    y + 17 - label.font.FONT_HEIGHT, 0xFFFFFF);
        } finally {
            label.font.setUnicodeFlag(unicode);
            // The rest of the overlay needs lighting and depth back on when we're done.
            GlStateManager.enableLighting();
            GlStateManager.enableDepth();
        }
    }

    private static final class Label {
        final SwapBinding binding;
        final FontRenderer font;
        final String text;
        final int width;

        Label(SwapBinding binding, FontRenderer font) {
            this.binding = binding;
            this.font = font;
            text = "§c§l" + SwapKeys.overlayName(binding.key);
            boolean unicode = font.getUnicodeFlag();
            font.setUnicodeFlag(true);
            try {
                width = FontWidths.measure(font, text);
            } finally {
                font.setUnicodeFlag(unicode);
            }
        }
    }
    private static final class CachedIdentity {
        private final Item item;
        private final int metadata;
        private final boolean present;
        private final long nonce;
        final ItemStack snapshot;
        final ItemIdentity identity;
        Label label;

        CachedIdentity(ItemStack stack) {
            item = stack.getItem();
            metadata = stack.getMetadata();
            present = stack.stackSize > 0;
            identity = ItemIdentity.read(stack);
            boolean byNonce = identity != null && identity.kind == ItemIdentity.Kind.NONCE;
            nonce = byNonce ? ItemIdentity.nonce(stack).getLong() : 0;
            // Only nonce-based identities can ignore other NBT changes.
            snapshot = byNonce ? null : stack.copy();
        }

        boolean matches(ItemStack stack) {
            if (stack.getItem() != item || item.getHasSubtypes() && stack.getMetadata() != metadata
                    || (stack.stackSize > 0) != present)
                return false;
            if (snapshot != null)
                return ItemStack.areItemStackTagsEqual(stack, snapshot);
            NBTPrimitive currentNonce = ItemIdentity.nonce(stack);
            return currentNonce != null && currentNonce.getLong() == nonce;
        }
    }
}
