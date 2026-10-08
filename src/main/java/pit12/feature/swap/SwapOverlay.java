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

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

final class SwapOverlay {
    private final Minecraft minecraft;
    private final BindingBook bindings;
    private final SwapConfig config;
    private final IdentityHashMap<ItemStack, Label> labels = new IdentityHashMap<>();
    private final IdentityHashMap<ItemStack, CachedIdentity> cache = new IdentityHashMap<>();

    SwapOverlay(Minecraft minecraft, BindingBook bindings, SwapConfig config) {
        this.minecraft = minecraft;
        this.bindings = bindings;
        this.config = config;
    }

    void clear() {
        labels.clear();
        cache.clear();
    }

    void refresh() {
        labels.clear();
        if (!config.highlight.get() || bindings.readinessProblem() != null || bindings.count() == 0
                || minecraft.thePlayer == null) {
            cache.clear();
            return;
        }
        IdentityHashMap<ItemStack, ItemIdentity> identities = new IdentityHashMap<>();
        boolean containerScreen = minecraft.currentScreen instanceof GuiContainer;
        for (Slot slot : minecraft.thePlayer.inventoryContainer.inventorySlots) {
            if (containerScreen || slot.slotNumber >= 36
                    || slot.slotNumber >= 5 && slot.slotNumber <= 8)
                addIdentity(identities, slot.getStack());
        }
        if (containerScreen) {
            for (Slot slot : minecraft.thePlayer.openContainer.inventorySlots) {
                addIdentity(identities, slot.getStack());
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
            String name = Keyboard.getKeyName(binding.key);
            labels.put(entry.getKey(), new Label("\u00a7c\u00a7l" + name, binding.equipment,
                    minecraft.fontRendererObj.getStringWidth(name)));
        }
    }

    private void addIdentity(IdentityHashMap<ItemStack, ItemIdentity> identities, ItemStack stack) {
        if (stack == null || identities.containsKey(stack))
            return;
        CachedIdentity cached = cache.get(stack);
        // NBT can change in place, so compare with a copy before reusing an identity.
        if (cached == null || stack.getItem() != cached.snapshot.getItem()
                || stack.getItem().getHasSubtypes()
                        && stack.getMetadata() != cached.snapshot.getMetadata()
                || !ItemStack.areItemStackTagsEqual(stack, cached.snapshot)
                || (stack.stackSize > 0) != (cached.snapshot.stackSize > 0)) {
            cached = new CachedIdentity(stack.copy(), ItemIdentity.read(stack));
            cache.put(stack, cached);
        }
        identities.put(stack, cached.identity);
    }

    void draw(ItemStack stack, int x, int y) {
        Label label = labels.get(stack);
        if (label == null || !config.highlight.get())
            return;
        int textX = label.equipment ? x + 14 - label.width : x + 2;
        // Vanilla expects lighting and depth to be enabled when this overlay returns.
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        minecraft.fontRendererObj.drawStringWithShadow(label.text, textX, y + 7, 0xFFFFFF);
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
    }

    private static final class Label {
        final String text;
        final boolean equipment;
        final int width;

        Label(String text, boolean equipment, int width) {
            this.text = text;
            this.equipment = equipment;
            this.width = width;
        }
    }
    private static final class CachedIdentity {
        final ItemStack snapshot;
        final ItemIdentity identity;

        CachedIdentity(ItemStack snapshot, ItemIdentity identity) {
            this.snapshot = snapshot;
            this.identity = identity;
        }
    }
}
