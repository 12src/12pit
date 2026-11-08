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
package pit12.platform.mixin.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.runtime.input.InputBlockBinding;
import pit12.runtime.input.InputBlocker;
import pit12.runtime.input.InputTickBinding;
import pit12.runtime.input.InputTicks;
import pit12.runtime.input.InputTicks.Phase;

// Run the input phases after Smart block.
@Mixin(value = Minecraft.class, priority = 900)
public abstract class MinecraftMixin implements InputBlockBinding, InputTickBinding {
    @Unique
    private volatile InputBlocker pit12$inputBlocker;
    @Unique
    private InputTicks pit12$inputTicks;

    @Override
    public void pit12$bindInputBlocker(InputBlocker blocker) {
        pit12$inputBlocker = blocker;
    }

    @Override
    public InputBlocker pit12$inputBlocker() {
        return pit12$inputBlocker;
    }

    @Override
    public void pit12$bindInputTicks(InputTicks ticks) {
        pit12$inputTicks = ticks;
    }

    @Inject(method = "runTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/FMLCommonHandler;onPreClientTick()V",
                    shift = At.Shift.AFTER, remap = false))
    private void pit12$startInputTick(CallbackInfo callback) {
        if (pit12$inputBlocker != null && pit12$inputBlocker.locked())
            pit12$inputBlocker.drainEvents();
        if (pit12$inputTicks != null)
            pit12$inputTicks.tick(Phase.START);
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiScreen;handleInput()V", shift = At.Shift.AFTER))
    private void pit12$afterGuiInput(CallbackInfo callback) {
        if (pit12$inputTicks != null)
            pit12$inputTicks.tick(Phase.GUI_INPUT);
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiScreen;updateScreen()V", shift = At.Shift.AFTER))
    private void pit12$afterGuiUpdate(CallbackInfo callback) {
        if (pit12$inputTicks != null)
            pit12$inputTicks.tick(Phase.GUI_UPDATE);
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z", ordinal = 0))
    private void pit12$beforeActions(CallbackInfo callback) {
        if (pit12$inputTicks != null)
            pit12$inputTicks.tick(Phase.BEFORE_ACTIONS);
    }

    @Inject(method = "runTick",
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;sendClickBlockToController(Z)V")),
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/Minecraft;theWorld:Lnet/minecraft/client/multiplayer/WorldClient;",
                    ordinal = 0))
    private void pit12$afterInput(CallbackInfo callback) {
        if (pit12$inputTicks != null)
            pit12$inputTicks.tick(Phase.AFTER_INPUT);
    }

    @Inject(method = {"clickMouse", "rightClickMouse", "middleClickMouse",
            "sendClickBlockToController"}, at = @At("HEAD"), cancellable = true)
    private void pit12$blockMouseActions(CallbackInfo callback) {
        if (pit12$inputBlocker != null && pit12$inputBlocker.blocksActions())
            callback.cancel();
    }

    @Inject(method = "setIngameFocus", at = @At("HEAD"), cancellable = true)
    private void pit12$keepCursorFree(CallbackInfo callback) {
        if (pit12$inputBlocker != null && pit12$inputBlocker.blocksFocus())
            callback.cancel();
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z", remap = false))
    private boolean pit12$nextKeyboardEvent() {
        while (Keyboard.next()) {
            if (pit12$inputBlocker == null || !pit12$inputBlocker.key(Keyboard.getEventKey(),
                    Keyboard.getEventKeyState(), Keyboard.isRepeatEvent()))
                return true;
        }
        return false;
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;next()Z", remap = false))
    private boolean pit12$nextMouseEvent() {
        while (Mouse.next()) {
            if (pit12$inputBlocker == null || !pit12$inputBlocker.mouse(Mouse.getEventButton(),
                    Mouse.getEventButtonState()))
                return true;
        }
        return false;
    }

    // Lunar wraps this call too.
    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiScreen;handleInput()V"))
    private void pit12$beforeGuiInput(CallbackInfo callback) {
        if (pit12$inputBlocker != null && pit12$inputBlocker.locked())
            pit12$inputBlocker.drainEvents();
    }
}
