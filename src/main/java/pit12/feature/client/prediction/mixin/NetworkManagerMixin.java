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
package pit12.feature.client.prediction.mixin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.client.prediction.PredictionMarginsFeature.Probe;
import pit12.feature.client.prediction.PredictionProbeBinding;

@Mixin(NetworkManager.class)
public abstract class NetworkManagerMixin implements PredictionProbeBinding {
    @Unique
    private volatile Probe pit12$predictionProbe;

    @Override
    public void bindPredictionProbe(Probe probe) {
        pit12$predictionProbe = probe;
    }

    @Inject(method = "dispatchPacket", at = @At("HEAD"))
    private void pit12$beforeProbeSend(Packet<?> packet,
            GenericFutureListener<? extends Future<? super Void>>[] listeners, CallbackInfo ci) {
        Probe probe = pit12$predictionProbe;
        if (probe != null)
            probe.sending(packet);
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V",
            at = @At("HEAD"))
    private void pit12$afterProbeReply(ChannelHandlerContext context, Packet<?> packet,
            CallbackInfo ci) {
        Probe probe = pit12$predictionProbe;
        if (probe != null)
            probe.received(packet);
    }
}
