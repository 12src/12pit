package pit12.platform.mixin.network;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.ClientEvents.Chat;

@Mixin(NetHandlerPlayClient.class)
public abstract class OrnitheNetHandlerPlayClientMixin {
    @Shadow
    public abstract NetworkManager getNetworkManager();

    @Inject(method = "handleChat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/play/server/S02PacketChat;getType()B", ordinal = 0))
    private void pit12$chatReceived(S02PacketChat packet, CallbackInfo callback) {
        if (!ClientEvents.CHAT.listening())
            return;
        Chat chat = new Chat(packet.getType(), packet.getChatComponent());
        ClientEvents.CHAT.publish(listener -> listener.accept(chat));
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void pit12$disconnected(IChatComponent reason, CallbackInfo callback) {
        NetworkManager manager = getNetworkManager();
        ClientEvents.DISCONNECT.publish(listener -> listener.accept(manager));
    }
}
