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
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.swap.SwapHooks;
import pit12.feature.swap.SwapHooksBinding;

@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientMixin {
    @Inject(method = "addToSendQueue", at = @At("HEAD"))
    private void pit12$beforeWindowClick(Packet<?> packet, CallbackInfo callback) {
        if (!(packet instanceof C0EPacketClickWindow))
            return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!minecraft.isCallingFromMinecraftThread() || (Object) this != minecraft.getNetHandler())
            return;
        SwapHooks hooks = ((SwapHooksBinding) minecraft).pit12$swapHooks();
        if (hooks != null) {
            C0EPacketClickWindow click = (C0EPacketClickWindow) packet;
            hooks.clickSent(click.getWindowId(), click.getActionNumber());
        }
    }

    // Vanilla handles rejected clicks first and schedules this handler on the client thread.
    @Inject(method = "handleConfirmTransaction", at = @At("TAIL"))
    private void pit12$afterInventoryConfirmation(S32PacketConfirmTransaction packet,
            CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if ((Object) this != minecraft.getNetHandler())
            return;
        SwapHooks hooks = ((SwapHooksBinding) minecraft).pit12$swapHooks();
        // MCP 22 leaves the acceptance getter unmapped.
        if (hooks != null)
            hooks.confirmClick(packet.getWindowId(), packet.getActionNumber(),
                    packet.func_148888_e());
    }

    // TAIL runs after vanilla moves packet handling onto the client thread; audio settings do not affect it.
    @Inject(method = "handleSoundEffect", at = @At("TAIL"))
    private void pit12$afterSoundEffect(S29PacketSoundEffect packet, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if ((Object) this != minecraft.getNetHandler())
            return;
        SwapHooks hooks = ((SwapHooksBinding) minecraft).pit12$swapHooks();
        if (hooks != null)
            hooks.sound(packet.getSoundName(), packet.getX(), packet.getY(), packet.getZ(),
                    packet.getVolume(), packet.getPitch());
    }
}
