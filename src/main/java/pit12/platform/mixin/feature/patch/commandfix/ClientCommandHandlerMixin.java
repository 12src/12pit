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
package pit12.platform.mixin.feature.patch.commandfix;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.feature.patch.commandfix.CommandFixBinding;
import pit12.feature.patch.commandfix.CommandFixConfig;

@Mixin(value = ClientCommandHandler.class, remap = false)
public abstract class ClientCommandHandlerMixin implements CommandFixBinding {
    @Unique
    private CommandFixConfig pit12$commandFixConfig;

    @Override
    public void pit12$bindCommandFixConfig(CommandFixConfig config) {
        pit12$commandFixConfig = config;
    }

    @Inject(method = "executeCommand", at = @At("HEAD"), cancellable = true, remap = true)
    private void pit12$requireSlash(ICommandSender sender, String message,
            CallbackInfoReturnable<Integer> cir) {
        if (pit12$commandFixConfig != null && pit12$commandFixConfig.enabled()
                && !message.startsWith("/")) {
            // Zero lets the chat caller send the message normally.
            cir.setReturnValue(0);
        }
    }
}
