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
package pit12.platform.mixin.client;

import java.util.List;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.platform.command.ClientCommandAdapter;

@Mixin(CommandHandler.class)
public abstract class CommandHandlerMixin {
    @Inject(method = "getTabCompletionOptions", at = @At("RETURN"))
    private void pit12$hideCommandAliases(ICommandSender sender, String input, BlockPos position,
            CallbackInfoReturnable<List<String>> cir) {
        if (input.indexOf(' ') >= 0)
            return;
        List<String> completions = cir.getReturnValue();
        for (ICommand command : ((CommandHandler) (Object) this).getCommands().values()) {
            if (command instanceof ClientCommandAdapter) {
                completions.removeAll(command.getCommandAliases());
            }
        }
    }
}
