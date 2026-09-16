package pit12.platform.mixin.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraftforge.fml.common.FMLModContainer;
import net.minecraftforge.fml.common.ModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pit12.platform.mixin.accessor.FMLModContainerAccessor;

@Mixin(targets = "net.minecraftforge.fml.common.network.handshake.FMLHandshakeClientState$2",
        remap = false)
public abstract class FMLHandshakeClientStateMixin {
    @ModifyArg(
            method = "accept(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraftforge/fml/common/network/handshake/FMLHandshakeMessage;)Lnet/minecraftforge/fml/common/network/handshake/FMLHandshakeClientState;",
            remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/network/handshake/FMLHandshakeMessage$ModList;<init>(Ljava/util/List;)V",
                    remap = false, ordinal = 0),
            index = 0, require = 1)
    private List<ModContainer> pit12$filterClientOnlyMods(List<ModContainer> mods) {
        List<ModContainer> filtered = new ArrayList<ModContainer>();
        for (ModContainer mod : mods) {
            if (!pit12$isClientSideOnly(mod)) {
                filtered.add(mod);
            }
        }
        return filtered;
    }

    private static boolean pit12$isClientSideOnly(ModContainer mod) {
        if (!(mod instanceof FMLModContainer)) {
            return false;
        }
        Map<String, Object> descriptor = ((FMLModContainerAccessor) mod).pit12$getDescriptor();
        return Boolean.TRUE.equals(descriptor.get("clientSideOnly"));
    }
}
