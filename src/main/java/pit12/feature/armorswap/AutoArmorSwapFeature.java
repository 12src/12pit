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
package pit12.feature.armorswap;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import pit12.feature.Feature;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.item.PitEnchantments;

/**
 * Swaps the held armor piece with the worn piece in its slot when the player right-clicks. The right click is polled
 * from the client tick before vanilla processes it, so the swap preempts the vanilla use by opening the player
 * inventory for a few ticks: one tick after opening (plus the configured swap delay) it sends the single number-key
 * swap click a player would make on the armor slot, then closes the inventory after the configured close delay. The
 * inventory can render transparently with the cursor hidden, and the player's movement input is reset every tick of the
 * swap so nothing interferes with the simulated clicks.
 */
public final class AutoArmorSwapFeature implements Feature {
    private static final Logger LOGGER = Logger.getLogger(AutoArmorSwapFeature.class.getName());
    private static final int PLAYER_WINDOW_ID = 0;
    private static final int SWAP_CLICK_MODE = 2;
    private static final int RIGHT_CLICK_BUTTON = 1;
    private static final int TRIGGER_TO_SWAP_TICKS = 1;
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_SWAP = 1;
    private static final int PHASE_CLOSE = 2;
    private final AutoArmorSwapConfig config;
    private boolean started;
    private boolean lastRightClickState;
    private int phase = PHASE_IDLE;
    private int ticksLeft;
    private int armorSlotId;
    private int armorInventoryIndex;
    private int hotbarButton;

    public AutoArmorSwapFeature(AutoArmorSwapConfig config) {
        this.config = config;
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        started = true;
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (phase != PHASE_IDLE && minecraft.currentScreen instanceof GuiInventory) {
            minecraft.displayGuiScreen(null);
        }
        phase = PHASE_IDLE;
        ticksLeft = 0;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (phase != PHASE_IDLE) {
            runSwapSequence(minecraft);
            return;
        }
        if (!config.enabled() || minecraft.currentScreen != null || minecraft.thePlayer == null) {
            return;
        }
        boolean rightClick = Mouse.isButtonDown(RIGHT_CLICK_BUTTON);
        boolean triggered = rightClick && !lastRightClickState;
        lastRightClickState = rightClick;
        if (triggered) {
            tryStartSwap(minecraft);
        }
    }

    private void tryStartSwap(Minecraft minecraft) {
        EntityPlayerSP player = minecraft.thePlayer;
        ItemStack held = player.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemArmor)) {
            return;
        }
        int armorPosition = EntityLiving.getArmorPosition(held);
        if (armorPosition < 1 || armorPosition > 4) {
            return;
        }
        if (isTrashPandaItem(held) || isTargetingInteractable(minecraft)) {
            return;
        }
        // GuiInventory armor slots count down from 9: helmet 5, chest 6, leggings 7, boots 8.
        armorSlotId = 9 - armorPosition;
        armorInventoryIndex = armorPosition - 1;
        hotbarButton = player.inventory.currentItem;
        if (config.hideCursor()) {
            Mouse.setGrabbed(true);
        }
        resetMovementState(minecraft);
        openInventoryGui(minecraft);
        if (config.hideCursor()) {
            Mouse.setGrabbed(true);
        }
        phase = PHASE_SWAP;
        ticksLeft = TRIGGER_TO_SWAP_TICKS + config.swapDelay();
        LOGGER.info("Armor swap scheduled: armor slot " + armorSlotId + " with hotbar button "
                + hotbarButton + ".");
    }

    private void runSwapSequence(Minecraft minecraft) {
        if (minecraft.thePlayer == null) {
            phase = PHASE_IDLE;
            return;
        }
        resetMovementState(minecraft);
        if (config.hideCursor() && !Mouse.isGrabbed()) {
            Mouse.setGrabbed(true);
        }
        ticksLeft--;
        if (ticksLeft > 0) {
            return;
        }
        if (phase == PHASE_SWAP) {
            performSwapClick(minecraft);
            phase = PHASE_CLOSE;
            ticksLeft = config.closeDelay();
            return;
        }
        if (phase == PHASE_CLOSE) {
            finalizeClose(minecraft);
            phase = PHASE_IDLE;
        }
    }

    private void performSwapClick(Minecraft minecraft) {
        try {
            minecraft.playerController.windowClick(PLAYER_WINDOW_ID, armorSlotId, hotbarButton,
                    SWAP_CLICK_MODE, minecraft.thePlayer);
            minecraft.thePlayer.inventoryContainer.detectAndSendChanges();
            minecraft.thePlayer.inventory.markDirty();
            LOGGER.info("Armor swap click sent.");
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Failed to send the armor swap click", failure);
        }
    }

    private void finalizeClose(Minecraft minecraft) {
        try {
            minecraft.getNetHandler().addToSendQueue(new C0DPacketCloseWindow(PLAYER_WINDOW_ID));
            minecraft.displayGuiScreen(null);
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Failed to close the simulated inventory", failure);
            return;
        }
        refreshInventory(minecraft);
        simulateKeyRefresh(minecraft);
        showSwapMessage(minecraft);
    }

    private void openInventoryGui(Minecraft minecraft) {
        try {
            minecraft.getNetHandler().addToSendQueue(new C16PacketClientStatus(
                    C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
            minecraft.displayGuiScreen(new GuiInventory(minecraft.thePlayer));
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Failed to open the simulated inventory", failure);
        }
    }

    private void refreshInventory(Minecraft minecraft) {
        try {
            minecraft.thePlayer.inventoryContainer.detectAndSendChanges();
            minecraft.thePlayer.inventory.markDirty();
            if (minecraft.currentScreen == null && minecraft.ingameGUI != null) {
                minecraft.ingameGUI.updateTick();
            }
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Failed to refresh the inventory after the swap", failure);
        }
    }

    @SubscribeEvent
    public void onGuiKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (phase != PHASE_IDLE && (config.transparent() || config.hideCursor())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGuiMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (phase != PHASE_IDLE && (config.transparent() || config.hideCursor())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGuiDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (phase == PHASE_IDLE) {
            return;
        }
        if (config.transparent()) {
            event.setCanceled(true);
        }
        if (config.hideCursor()) {
            Mouse.setCursorPosition(-1, -1);
        }
    }

    private void resetMovementState(Minecraft minecraft) {
        if (minecraft.thePlayer == null || minecraft.thePlayer.movementInput == null) {
            return;
        }
        minecraft.thePlayer.movementInput.moveForward = 0.0F;
        minecraft.thePlayer.movementInput.moveStrafe = 0.0F;
        minecraft.thePlayer.movementInput.jump = false;
        minecraft.thePlayer.movementInput.sneak = false;
        minecraft.thePlayer.setSprinting(false);
    }

    /**
     * Re-presses every movement key the player still physically holds, because the frozen GUI input never updated the key
     * bindings while the swap inventory was open.
     */
    private void simulateKeyRefresh(Minecraft minecraft) {
        try {
            KeyBinding[] movementKeys = {minecraft.gameSettings.keyBindForward,
                    minecraft.gameSettings.keyBindBack, minecraft.gameSettings.keyBindLeft,
                    minecraft.gameSettings.keyBindRight, minecraft.gameSettings.keyBindJump,
                    minecraft.gameSettings.keyBindSneak};
            for (KeyBinding movementKey : movementKeys) {
                if (isKeyPhysicallyPressed(movementKey)) {
                    KeyBinding.setKeyBindState(movementKey.getKeyCode(), false);
                    KeyBinding.setKeyBindState(movementKey.getKeyCode(), true);
                }
            }
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Failed to refresh movement keys after the swap", failure);
        }
    }

    private boolean isKeyPhysicallyPressed(KeyBinding keyBinding) {
        try {
            int keyCode = keyBinding.getKeyCode();
            return keyCode != 0 ? Keyboard.isKeyDown(keyCode) : keyBinding.isKeyDown();
        } catch (RuntimeException failure) {
            return keyBinding.isKeyDown();
        }
    }

    private void showSwapMessage(Minecraft minecraft) {
        if (!config.showSwapMessages()) {
            return;
        }
        ItemStack newEquipment = minecraft.thePlayer.inventory.armorInventory[armorInventoryIndex];
        if (newEquipment == null) {
            return;
        }
        String display = newEquipment.getDisplayName();
        PitEnchantments enchantments = PitEnchantmentReader.read(newEquipment);
        if (!enchantments.isEmpty()) {
            display = display + EnumChatFormatting.GRAY + " (" + enchantments.formatDisplayNames()
                    + EnumChatFormatting.GRAY + ")";
        }
        minecraft.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[12pit]"
                + EnumChatFormatting.RESET + " Equipped " + EnumChatFormatting.WHITE + display));
    }

    private boolean isTrashPandaItem(ItemStack stack) {
        String displayName = stack.getDisplayName();
        if (displayName != null && displayName.contains("Trash Panda")) {
            return true;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("display", 10)) {
            return false;
        }
        NBTTagCompound display = tag.getCompoundTag("display");
        if (!display.hasKey("Lore", 9)) {
            return false;
        }
        NBTTagList lore = display.getTagList("Lore", 8);
        for (int index = 0; index < lore.tagCount(); index++) {
            if (lore.getStringTagAt(index).contains("Trash Panda")) {
                return true;
            }
        }
        return false;
    }

    private boolean isTargetingInteractable(Minecraft minecraft) {
        if (minecraft.objectMouseOver == null) {
            return false;
        }
        if (minecraft.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            Entity entity = minecraft.objectMouseOver.entityHit;
            return entity instanceof EntityVillager || entity instanceof IMerchant
                    || entity instanceof EntityHorse || entity instanceof EntityAnimal;
        }
        if (minecraft.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }
        try {
            BlockPos blockPos = minecraft.objectMouseOver.getBlockPos();
            Block block = minecraft.theWorld.getBlockState(blockPos).getBlock();
            return block instanceof BlockChest || block instanceof BlockEnderChest
                    || block instanceof BlockAnvil || block instanceof BlockWorkbench
                    || block instanceof BlockContainer
                    || block.hasTileEntity(minecraft.theWorld.getBlockState(blockPos));
        } catch (RuntimeException failure) {
            // When the check fails, skip the swap rather than stealing the block interaction.
            return true;
        }
    }
}
