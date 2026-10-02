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
package pit12.platform.mixin.feature.swap;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.swap.SwapHooks;
import pit12.feature.swap.SwapHooksBinding;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements SwapHooksBinding {
    @Unique
    private SwapHooks pit12$swapHooks;

    @Override
    public void pit12$bindSwapHooks(SwapHooks hooks) {
        pit12$swapHooks = hooks;
    }

    @Override
    public SwapHooks pit12$swapHooks() {
        return pit12$swapHooks;
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z", remap = false))
    private boolean pit12$nextKeyboardEvent() {
        if (pit12$swapHooks == null || !pit12$swapHooks.inputLocked()) {
            return Keyboard.next();
        }
        while (Keyboard.next()) {
        }
        return false;
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/input/Keyboard;isKeyDown(I)Z", remap = false))
    private boolean pit12$heldKeyboardKey(int key) {
        return (pit12$swapHooks == null || !pit12$swapHooks.inputLocked())
                && Keyboard.isKeyDown(key);
    }

    @Redirect(method = {"runTick", "dispatchKeypresses"}, at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/input/Keyboard;getEventKeyState()Z", remap = false))
    private boolean pit12$swapKeyState() {
        boolean pressed = Keyboard.getEventKeyState();
        return (pit12$swapHooks == null
                || !pit12$swapHooks.key(Keyboard.getEventKey(), pressed, Keyboard.isRepeatEvent()))
                && pressed;
    }
}
