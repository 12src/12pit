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

import java.util.Random;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import pit12.runtime.input.InputTicks;
import pit12.shared.input.Keybind;
import pit12.shared.lifecycle.ClientLifecycle;

public final class AutoClickerFeature implements ClientLifecycle, InputTicks.Listener {
    private static final Logger LOGGER = LogManager.getLogger(AutoClickerFeature.class);
    private final Minecraft minecraft;
    private final AutoClickerConfig config;
    private final InputTicks inputTicks;
    private final Random random = new Random();
    private int currentTick;
    private int lastLeftClickTick;
    private int lastRightClickTick;
    private int lastCakeClickTick;
    private boolean wasLeftKeyDown;
    private boolean wasRightKeyDown;
    private boolean started;

    public AutoClickerFeature(Minecraft minecraft, AutoClickerConfig config,
            InputTicks inputTicks) {
        this.minecraft = minecraft;
        this.config = config;
        this.inputTicks = inputTicks;
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        inputTicks.addListener(this);
        resetState();
    }

    @Override
    public void stop() {
        started = false;
        inputTicks.removeListener(this);
        resetState();
    }

    @Override
    public void onInputTick(InputTicks.Phase phase) {
        if (phase != InputTicks.Phase.START || !config.enabled()) {
            return;
        }
        currentTick++;
        if (!isValidGameState()) {
            return;
        }
        try {
            handleKeybindToggles();
            if (config.cakeClick().get() && tryPerformCakeClick()) {
                return;
            }
            if (config.rightClick().get() && tryPerformRightClick()) {
                return;
            }
            if (config.leftClick().get() && tryPerformLeftClick()) {
                return;
            }
        } catch (RuntimeException failure) {
            LOGGER.error("Auto clicker tick failed", failure);
        }
    }

    private boolean isValidGameState() {
        return minecraft.theWorld != null && minecraft.thePlayer != null
                && minecraft.currentScreen == null;
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

    private boolean tryPerformCakeClick() {
        if (!isLookingAtCake()) {
            return false;
        }
        boolean leftMode = config.cakeMode().get() == 0;
        if (config.cakeHoldingOnly().get() && !Mouse.isButtonDown(leftMode ? 0 : 1)) {
            return false;
        }
        int delay = calculateTickDelay(config.cakeCpsMin().get(), config.cakeCpsMax().get());
        if (currentTick - lastCakeClickTick < delay) {
            return false;
        }
        if (leftMode) {
            press(minecraft.gameSettings.keyBindAttack);
        } else {
            press(minecraft.gameSettings.keyBindUseItem);
        }
        lastCakeClickTick = currentTick;
        return true;
    }

    private boolean tryPerformRightClick() {
        if (isLookingAtCake()) {
            return false;
        }
        if (config.rightHoldingOnly().get() && !Mouse.isButtonDown(1)) {
            return false;
        }
        if (config.rightBlocksOnly().get() && !isHoldingBlock()) {
            return false;
        }
        int delay = calculateTickDelay(config.rightCpsMin().get(), config.rightCpsMax().get());
        if (currentTick - lastRightClickTick < delay) {
            return false;
        }
        press(minecraft.gameSettings.keyBindUseItem);
        lastRightClickTick = currentTick;
        return true;
    }

    private boolean tryPerformLeftClick() {
        if (isLookingAtCake()) {
            return false;
        }
        if (config.leftHoldingOnly().get() && !Mouse.isButtonDown(0)) {
            return false;
        }
        if (config.leftWeaponsOnly().get() && !isHoldingWeapon()) {
            return false;
        }
        int delay = calculateTickDelay(config.leftCpsMin().get(), config.leftCpsMax().get());
        if (currentTick - lastLeftClickTick < delay) {
            return false;
        }
        press(minecraft.gameSettings.keyBindAttack);
        lastLeftClickTick = currentTick;
        return true;
    }

    private void press(KeyBinding binding) {
        int keyCode = binding.getKeyCode();
        boolean held =
                keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode);
        KeyBinding.setKeyBindState(keyCode, true);
        KeyBinding.onTick(keyCode);
        KeyBinding.setKeyBindState(keyCode, held);
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

    /** Converts a click rate to a tick delay; 1 tick is the lowest delay (20 CPS). */
    private int calculateTickDelay(int minCps, int maxCps) {
        if (minCps > maxCps) {
            int swap = minCps;
            minCps = maxCps;
            maxCps = swap;
        }
        int cps = minCps + random.nextInt(maxCps - minCps + 1);
        return Math.max(1, Math.round(20.0F / cps));
    }

    private void resetState() {
        currentTick = 0;
        lastLeftClickTick = 0;
        lastRightClickTick = 0;
        lastCakeClickTick = 0;
        wasLeftKeyDown = false;
        wasRightKeyDown = false;
    }
}
