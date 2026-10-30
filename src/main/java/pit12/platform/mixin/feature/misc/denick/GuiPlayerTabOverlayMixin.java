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
package pit12.platform.mixin.feature.misc.denick;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.feature.misc.denick.DenickNamesBinding;
import pit12.feature.misc.denick.api.DenickNames;

@Mixin(GuiPlayerTabOverlay.class)
public abstract class GuiPlayerTabOverlayMixin {
    @Unique
    private DenickNames pit12$renderingNames;

    // Only decorate render calls. PlayerNameCache also calls getPlayerName.
    @Inject(method = "renderPlayerlist", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;"))
    private void pit12$beginName(CallbackInfo callback) {
        DenickNames names = ((DenickNamesBinding) Minecraft.getMinecraft()).pit12$denickNames();
        if (names != null) {
            pit12$renderingNames = names;
        }
    }

    @Inject(method = "renderPlayerlist", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;",
            shift = At.Shift.AFTER))
    private void pit12$endName(CallbackInfo callback) {
        pit12$renderingNames = null;
    }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void pit12$displayName(NetworkPlayerInfo player,
            CallbackInfoReturnable<String> callback) {
        if (pit12$renderingNames == null) {
            return;
        }
        String original = callback.getReturnValue();
        String name = pit12$renderingNames.listName(player.getGameProfile().getId(), original);
        if (!name.equals(original)) {
            callback.setReturnValue(name);
        }
    }
}
