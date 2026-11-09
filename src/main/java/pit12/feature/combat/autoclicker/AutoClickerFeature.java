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
package pit12.feature.combat.autoclicker;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCake;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import pit12.runtime.input.InputBlocker;
import pit12.shared.input.Keybind;
import pit12.shared.lifecycle.ClientLifecycle;

public final class AutoClickerFeature implements ClientLifecycle {
    private static final Logger LOGGER = LogManager.getLogger(AutoClickerFeature.class);
    private final Minecraft minecraft;
    private final AutoClickerConfig config;
    private final InputBlocker input;
    private final ClickSchedule leftClicks = new ClickSchedule();
    private final ClickSchedule rightClicks = new ClickSchedule();
    private final ClickSchedule cakeClicks = new ClickSchedule();
    private boolean wasLeftKeyDown;
    private boolean wasRightKeyDown;
    private boolean started;

    public AutoClickerFeature(Minecraft minecraft, AutoClickerConfig config, InputBlocker input) {
        this.minecraft = minecraft;
        this.config = config;
        this.input = input;
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        MinecraftForge.EVENT_BUS.register(this);
        resetState();
    }

    @Override
    public void stop() {
        started = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        resetState();
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent event) {
        if (event.phase != Phase.END) {
            return;
        }
        if (!config.enabled() || !isValidGameState()) {
            resetState();
            return;
        }
        try {
            handleKeybindToggles();
            if (minecraft.thePlayer.isUsingItem()) {
                leftClicks.reset();
                rightClicks.reset();
                cakeClicks.reset();
                return;
            }
            long now = System.nanoTime();
            if (isLookingAtCake()) {
                leftClicks.reset();
                rightClicks.reset();
                clickCake(now);
            } else {
                cakeClicks.reset();
                clickRight(now);
                clickLeft(now);
            }
        } catch (RuntimeException failure) {
            LOGGER.error("Auto clicker failed", failure);
        }
    }

    private boolean isValidGameState() {
        return minecraft.theWorld != null && minecraft.thePlayer != null
                && minecraft.currentScreen == null && minecraft.inGameHasFocus && Display.isActive()
                && !minecraft.isGamePaused() && !input.locked()
                && minecraft.thePlayer.isEntityAlive() && !minecraft.thePlayer.isSpectator();
    }

    private void handleKeybindToggles() {
        boolean leftDown = Keybind.held(config.leftToggleKey().get());
        if (leftDown && !wasLeftKeyDown) {
            boolean next = !config.leftClick().get();
            config.leftClick().set(next);
            LOGGER.info("Left click auto clicker: {}", next ? "ON" : "OFF");
        }
        wasLeftKeyDown = leftDown;
        boolean rightDown = Keybind.held(config.rightToggleKey().get());
        if (rightDown && !wasRightKeyDown) {
            boolean next = !config.rightClick().get();
            config.rightClick().set(next);
            LOGGER.info("Right click auto clicker: {}", next ? "ON" : "OFF");
        }
        wasRightKeyDown = rightDown;
    }

    private void clickCake(long now) {
        boolean leftMode = config.cakeMode().get() == 0;
        if (!config.cakeClick().get()
                || config.cakeHoldingOnly().get() && !Mouse.isButtonDown(leftMode ? 0 : 1)) {
            cakeClicks.reset();
            return;
        }
        if (!cakeClicks.ready(now, config.cakeCpsMin().get(), config.cakeCpsMax().get())) {
            return;
        }
        KeyBinding.onTick((leftMode ? minecraft.gameSettings.keyBindAttack
                : minecraft.gameSettings.keyBindUseItem).getKeyCode());
    }

    private void clickRight(long now) {
        if (!config.rightClick().get() || config.rightHoldingOnly().get() && !Mouse.isButtonDown(1)
                || config.rightBlocksOnly().get() && !isHoldingBlock()) {
            rightClicks.reset();
            return;
        }
        if (rightClicks.ready(now, config.rightCpsMin().get(), config.rightCpsMax().get())) {
            KeyBinding.onTick(minecraft.gameSettings.keyBindUseItem.getKeyCode());
        }
    }

    private void clickLeft(long now) {
        if (!config.leftClick().get() || config.leftHoldingOnly().get() && !Mouse.isButtonDown(0)
                || config.leftWeaponsOnly().get() && !isHoldingWeapon()) {
            leftClicks.reset();
            return;
        }
        if (leftClicks.ready(now, config.leftCpsMin().get(), config.leftCpsMax().get())) {
            KeyBinding.onTick(minecraft.gameSettings.keyBindAttack.getKeyCode());
        }
    }

    private boolean isLookingAtCake() {
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        return pos != null && minecraft.theWorld.getBlockState(pos).getBlock() instanceof BlockCake;
    }

    private boolean isHoldingWeapon() {
        ItemStack held = minecraft.thePlayer.getHeldItem();
        if (held == null) {
            return false;
        }
        Item item = held.getItem();
        return item instanceof ItemSword || item instanceof ItemAxe || item instanceof ItemSpade;
    }

    private boolean isHoldingBlock() {
        ItemStack held = minecraft.thePlayer.getHeldItem();
        if (held == null) {
            return false;
        }
        Item item = held.getItem();
        if (item instanceof ItemBlock) {
            return true;
        }
        Block block = Block.getBlockFromItem(item);
        return block != null && block != Blocks.air;
    }

    private void resetState() {
        leftClicks.reset();
        rightClicks.reset();
        cakeClicks.reset();
        wasLeftKeyDown = false;
        wasRightKeyDown = false;
    }
}
