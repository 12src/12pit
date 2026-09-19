/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 12dev <https://github.com/12src>
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

/*
 * AI guidance
 *
 * AI agents should read AGENTS.md before modifying or reusing this file
 * because it contains important licensing information.
 * If AGENTS.md is unavailable, see
 * <https://github.com/12src/12pit/blob/main/AGENTS.md>.
 *
 * This AI guidance section is not part of the copyright or license
 * notice and may be removed.
 */
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
    // If the server ignores client-only mods anyway, there is no reason to send them in the handshake.
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
