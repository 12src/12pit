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
package pit12.feature.combat.autouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;
import pit12.feature.combat.autouse.AutoUseController.PullOperation;
import pit12.runtime.input.InputBlocker;
import pit12.runtime.input.InputTicks.Phase;
import pit12.runtime.input.InputTicks.Timing;
import pit12.runtime.item.InventoryScreen;
import pit12.runtime.languages.Languages;
import pit12.shared.chat.ChatFeedback;
import pit12.shared.chat.ChatFeedback.Tone;

final class AutoUseInventory implements PullOperation {
    private static final Logger LOGGER = LogManager.getLogger(AutoUseInventory.class);
    private final Minecraft minecraft;
    private final AutoUseConfig config;
    private final AutoUseController controller;
    private final InputBlocker input;
    private final InventoryScreen screen;
    private final Languages language;
    private final Timing clickTiming =
            new Timing(Phase.GUI_INPUT, Phase.GUI_UPDATE, Phase.AFTER_INPUT);
    private EntityPlayerSP player;
    private long revision;
    private long tick;
    private long nextClickTick;
    private long resumeAt = -1;
    private Transfer transfer;
    private boolean paused;

    AutoUseInventory(Minecraft minecraft, AutoUseConfig config, AutoUseController controller,
            InputBlocker input, Languages language) {
        this.minecraft = minecraft;
        this.config = config;
        this.controller = controller;
        this.input = input;
        this.language = language;
        screen = new InventoryScreen(minecraft, input, this);
    }

    void updatePlayer(EntityPlayerSP current, long currentRevision) {
        if (player == current && revision == currentRevision)
            return;
        closeScreen(false, false);
        player = current;
        revision = currentRevision;
        nextClickTick = 0;
        paused = false;
    }

    void tick() {
        tick++;
    }

    boolean hasWork() {
        return transfer != null;
    }

    boolean available() {
        return !hasWork() && !paused && screen.canUse(player);
    }

    boolean hides(GuiScreen current) {
        return transfer != null && transfer.hidden && screen.owns(current);
    }

    void resumeTransfers() {
        paused = false;
    }

    @Override
    public void move(int sourceSlot, int hotbarSlot) {
        transfer = new Transfer(sourceSlot, hotbarSlot, player.inventory, tick,
                config.inventoryDisplay.get() == 0);
        clickTiming.reset();
        boolean opened = false;
        try {
            screen.reserve(player);
            opened = screen.open();
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not open the inventory for Auto Use", failure);
        }
        if (!opened) {
            fail(language.translate("Could not open the inventory."));
            return;
        }
        advance(Phase.AFTER_INPUT, true);
    }

    void advance(Phase phase, boolean enabled) {
        Transfer request = transfer;
        if (request == null)
            return;
        if (resumeAt >= 0) {
            if (phase == Phase.AFTER_INPUT && tick >= resumeAt)
                releaseInput(true);
            return;
        }
        if (!screen.canClick() || input.blockedFor(this) || !Display.isActive()) {
            closeScreen(true, false);
            return;
        }
        screen.updateMouseGrab(request.hidden);
        if (request.closeAt >= 0) {
            if (tick >= request.closeAt)
                closeScreen(true, config.resumeInputNextTick.get());
        } else if (!enabled || !request.matches(player.inventory) || !controller.readyFromInventory(
                player.inventory.getStackInSlot(request.sourceSlot), player.getHealth())) {
            closeScreen(true, false);
        } else if (tick - request.openedAt >= config.openDelay.get() && tick >= nextClickTick
                && clickTiming.ready(phase, config.randomClickTiming.get())) {
            click(request);
        }
    }

    private void click(Transfer request) {
        try {
            // Main inventory slot indices agree in InventoryPlayer and ContainerPlayer.
            screen.swapWithHotbar(request.sourceSlot, request.hotbarSlot);
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not move an item for Auto Use", failure);
            fail(language.translate("Could not move the item."));
            return;
        }
        nextClickTick = tick + config.swapDelay.get();
        request.closeAt = tick + config.closeDelay.get();
        if (request.closeAt == tick)
            closeScreen(true, config.resumeInputNextTick.get());
    }

    private void fail(String message) {
        paused = true;
        closeScreen(true, false);
        if (player == minecraft.thePlayer)
            ChatFeedback.reply(player, Tone.WARNING,
                    language.format("Auto Use: {0} Inventory transfers are paused.", message));
    }

    private void closeScreen(boolean sameSession, boolean waitToResume) {
        if (transfer == null)
            return;
        try {
            screen.close(sameSession);
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not close the inventory for Auto Use", failure);
        } finally {
            if (waitToResume && minecraft.currentScreen == null)
                resumeAt = tick + 1;
            else
                releaseInput(sameSession);
        }
    }

    private void releaseInput(boolean sameSession) {
        transfer = null;
        resumeAt = -1;
        screen.release(sameSession);
    }

    private static final class Transfer {
        private final int sourceSlot;
        private final int hotbarSlot;
        private final ItemStack sourceItem;
        private final ItemStack hotbarItem;
        private final long openedAt;
        private final boolean hidden;
        private long closeAt = -1;

        Transfer(int sourceSlot, int hotbarSlot, InventoryPlayer inventory, long openedAt,
                boolean hidden) {
            this.sourceSlot = sourceSlot;
            this.hotbarSlot = hotbarSlot;
            this.openedAt = openedAt;
            this.hidden = hidden;
            sourceItem = inventory.getStackInSlot(sourceSlot).copy();
            ItemStack occupied = inventory.getStackInSlot(hotbarSlot);
            hotbarItem = occupied == null ? null : occupied.copy();
        }

        boolean matches(InventoryPlayer inventory) {
            return ItemStack.areItemStacksEqual(sourceItem, inventory.getStackInSlot(sourceSlot))
                    && ItemStack.areItemStacksEqual(hotbarItem,
                            inventory.getStackInSlot(hotbarSlot));
        }
    }
}
