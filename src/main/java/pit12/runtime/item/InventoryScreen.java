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

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.network.play.client.C16PacketClientStatus;
import org.lwjgl.input.Mouse;
import pit12.runtime.input.InputBlocker;

public final class InventoryScreen {
    private final Minecraft minecraft;
    private final InputBlocker input;
    private final Object owner;
    private EntityPlayerSP player;
    private GuiInventory screen;
    private boolean mouseGrabbed;
    private boolean sendingClick;

    public InventoryScreen(Minecraft minecraft, InputBlocker input, Object owner) {
        this.minecraft = minecraft;
        this.input = input;
        this.owner = owner;
    }

    public boolean hasScreen() {
        return screen != null;
    }

    public boolean owns(GuiScreen candidate) {
        return screen != null && screen == candidate;
    }

    public boolean canUse(EntityPlayerSP player) {
        return (minecraft.currentScreen == null || owns(minecraft.currentScreen))
                && inventoryReady(player);
    }

    public void reserve(EntityPlayerSP player) {
        this.player = player;
        input.lock(owner);
    }

    public boolean open() {
        player.sendQueue.addToSendQueue(new C16PacketClientStatus(
                C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
        screen = new GuiInventory(player);
        minecraft.displayGuiScreen(screen);
        return canClick();
    }

    public boolean canClick() {
        return owns(minecraft.currentScreen) && player == minecraft.thePlayer
                && minecraft.theWorld != null && inventoryReady(player);
    }

    public void swapWithHotbar(int containerSlot, int hotbarIndex) {
        click(containerSlot, hotbarIndex, 2);
    }

    public void shiftClick(int containerSlot) {
        click(containerSlot, 0, 1);
    }

    public boolean sendingClick(int windowId) {
        return sendingClick && windowId == player.inventoryContainer.windowId;
    }

    private void click(int slot, int button, int mode) {
        if (!canClick())
            throw new IllegalStateException("Inventory changed");
        sendingClick = true;
        try {
            input.runAction(owner, () -> minecraft.playerController
                    .windowClick(player.inventoryContainer.windowId, slot, button, mode, player));
        } finally {
            sendingClick = false;
        }
    }

    public void close(boolean sameSession) {
        try {
            if (owns(minecraft.currentScreen))
                closeOwnedScreen(sameSession);
        } finally {
            screen = null;
            updateMouseGrab(false);
        }
    }

    private void closeOwnedScreen(boolean sameSession) {
        try {
            if (sameSession && player == minecraft.thePlayer && minecraft.theWorld != null
                    && player.openContainer == player.inventoryContainer)
                player.closeScreen();
            else
                minecraft.displayGuiScreen(null);
        } finally {
            if (owns(minecraft.currentScreen)) {
                // A screen listener can cancel closing.
                minecraft.currentScreen = null;
                if (minecraft.thePlayer != null && minecraft.theWorld != null)
                    minecraft.setIngameFocus();
            }
        }
    }

    public void updateMouseGrab(boolean hide) {
        boolean hidden = hide && owns(minecraft.currentScreen);
        if (hidden && !Mouse.isGrabbed())
            Mouse.setGrabbed(true);
        else if (!hidden && mouseGrabbed && minecraft.currentScreen != null && Mouse.isGrabbed())
            Mouse.setGrabbed(false);
        mouseGrabbed = hidden;
    }

    public void release(boolean sameSession) {
        EntityPlayerSP previous = player;
        if (previous == null)
            return;
        player = null;
        sendingClick = false;
        input.unlock(owner,
                sameSession && previous == minecraft.thePlayer && minecraft.theWorld != null
                        && minecraft.currentScreen == null && inventoryReady(previous));
    }

    private static boolean inventoryReady(EntityPlayerSP player) {
        return player.openContainer == player.inventoryContainer
                && player.inventory.getItemStack() == null;
    }
}
