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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.runtime.input.InputBlockBinding;
import pit12.runtime.input.InputBlocker;

@Mixin(value = Minecraft.class, priority = -1)
public abstract class MinecraftRedirect {
    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z", remap = false))
    private boolean pit12$nextKeyboardEvent() {
        while (Keyboard.next()) {
            InputBlocker blocker = ((InputBlockBinding) this).pit12$inputBlocker();
            if (blocker == null || !blocker.key(Keyboard.getEventKey(), Keyboard.getEventKeyState(),
                    Keyboard.isRepeatEvent()))
                return true;
        }
        return false;
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;next()Z", remap = false))
    private boolean pit12$nextMouseEvent() {
        while (Mouse.next()) {
            InputBlocker blocker = ((InputBlockBinding) this).pit12$inputBlocker();
            if (blocker == null
                    || !blocker.mouse(Mouse.getEventButton(), Mouse.getEventButtonState()))
                return true;
        }
        return false;
    }
}
