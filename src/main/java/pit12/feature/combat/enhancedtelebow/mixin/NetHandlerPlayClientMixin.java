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
import net.minecraft.client.network.NetHandlerPlayClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.combat.enhancedtelebow.EnhancedTelebowBinding;
import pit12.feature.combat.enhancedtelebow.EnhancedTelebowFeature;

@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientMixin {
    @Shadow
    private Minecraft gameController;
    @Shadow
    private boolean doneLoadingTerrain;

    @Inject(method = "handlePlayerPosLook", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
            shift = At.Shift.AFTER))
    private void pit12$beforePositionLook(CallbackInfo ci) {
        EnhancedTelebowFeature feature =
                ((EnhancedTelebowBinding) gameController).pit12$enhancedTelebow();
        if (feature != null && (Object) this == gameController.getNetHandler())
            feature.beforePositionLook(doneLoadingTerrain ? gameController.thePlayer : null);
    }

    @Inject(method = "handlePlayerPosLook", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkManager;sendPacket(Lnet/minecraft/network/Packet;)V",
            shift = At.Shift.AFTER))
    private void pit12$afterTeleportReply(CallbackInfo ci) {
        EnhancedTelebowFeature feature =
                ((EnhancedTelebowBinding) gameController).pit12$enhancedTelebow();
        if (feature != null && (Object) this == gameController.getNetHandler())
            feature.afterPositionLook();
    }
}
