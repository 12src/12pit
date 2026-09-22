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
package pit12.platform.mixin.player;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.runtime.player.PlayerEquipmentPacketBinding;
import pit12.runtime.player.PlayerEquipmentPacketObserver;

@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientEquipmentMixin implements PlayerEquipmentPacketBinding {
    @Unique
    private PlayerEquipmentPacketObserver pit12$playerEquipmentObserver;

    @Override
    public void bindPlayerEquipmentObserver(PlayerEquipmentPacketObserver observer) {
        pit12$playerEquipmentObserver = observer;
    }

    @Inject(method = "handleEntityEquipment", at = @At("TAIL"))
    private void pit12$afterEntityEquipment(S04PacketEntityEquipment packet, CallbackInfo ci) {
        PlayerEquipmentPacketObserver observer = pit12$playerEquipmentObserver;
        if (observer != null) {
            observer.onEquipmentPacket(packet.getEntityID(), packet.getEquipmentSlot());
        }
    }
}
