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
package pit12.feature.combat.enhancedtelebow.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import pit12.feature.combat.enhancedtelebow.EnhancedTelebowBinding;
import pit12.feature.combat.enhancedtelebow.EnhancedTelebowFeature;
import pit12.feature.combat.enhancedtelebow.TelebowRelease;

@Mixin(value = Minecraft.class, priority = -1)
public abstract class MinecraftRedirect {
    @Shadow
    private int leftClickCounter;

    @Redirect(method = "runTick", slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z", ordinal = 0)),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z", ordinal = 0),
            require = 1)
    private boolean pit12$keepDrawingTelebow(KeyBinding useKey) {
        TelebowRelease release = pit12$telebowRelease();
        boolean down = useKey.isKeyDown();
        if (release == null)
            return down;
        boolean keepDrawing = release.keepDrawing(down);
        // Vanilla still checks the held attack button for mining after release.
        if (!keepDrawing && release.bypassing() && leftClickCounter == 0)
            leftClickCounter = 1;
        return keepDrawing;
    }

    @Redirect(method = "runTick", slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z", ordinal = 0)),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z", ordinal = 1),
            require = 1)
    private boolean pit12$allowTelebowRedraw(KeyBinding useKey) {
        TelebowRelease release = pit12$telebowRelease();
        boolean down = useKey.isKeyDown();
        return release == null ? down : release.allowAutoDraw(down);
    }

    @Unique
    private TelebowRelease pit12$telebowRelease() {
        EnhancedTelebowFeature feature = ((EnhancedTelebowBinding) this).pit12$enhancedTelebow();
        return feature == null ? null : feature.release();
    }
}
