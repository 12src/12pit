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
package pit12.feature.combat.enhancedtelebow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;

public final class TelebowRelease {
    private final Minecraft minecraft;
    private EntityPlayerSP player;
    private ItemStack bow;
    private boolean bypass;

    TelebowRelease(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public boolean keepDrawing(boolean useKeyDown) {
        EntityPlayerSP current = minecraft.thePlayer;
        ItemStack held = current.getItemInUse();
        if (bow != null && (current != player || held != bow))
            reset();
        if (minecraft.currentScreen != null || !minecraft.inGameHasFocus || current.isRiding()
                || held == null || held != current.getHeldItem()
                || !(held.getItem() instanceof ItemBow)) {
            reset();
            return useKeyDown;
        }
        // Vanilla discards attack presses while an item is in use.
        boolean leftClick = minecraft.gameSettings.keyBindAttack.isPressed();
        if (useKeyDown && !leftClick && !bypass) {
            reset();
            return true;
        }
        if (bow == null && !PitEnchantmentReader.contains(held, PitEnchantment.Telebow))
            return useKeyDown;
        player = current;
        bow = held;
        bypass |= leftClick;
        boolean sentSneak = ((TelebowPlayer) current).pit12$sentSneakState();
        if (bypass)
            return sentSneak && !minecraft.gameSettings.keyBindSneak.isKeyDown();
        // The sneak update is sent after Minecraft checks whether to release the bow.
        return !sentSneak;
    }

    public boolean bypassing() {
        return bypass;
    }

    public boolean allowAutoDraw(boolean useKeyDown) {
        if (bow != null && (player != minecraft.thePlayer || bow != player.getHeldItem()
                || (!player.isUsingItem() && (!bypass || !useKeyDown))))
            reset();
        return useKeyDown && !bypass;
    }

    public boolean shouldSneak(EntityPlayerSP current) {
        return bow != null && player == current && !bypass && minecraft.currentScreen == null
                && minecraft.inGameHasFocus && bow == current.getItemInUse()
                && bow == current.getHeldItem();
    }

    void clearOutsideGameplay() {
        if (bow != null && (player != minecraft.thePlayer || minecraft.currentScreen != null
                || !minecraft.inGameHasFocus))
            reset();
    }

    void reset() {
        bow = null;
        player = null;
        bypass = false;
    }
}
