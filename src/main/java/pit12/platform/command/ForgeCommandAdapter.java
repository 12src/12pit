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
package pit12.platform.command;

import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.ClientCommandHandler;
import pit12.runtime.command.CommandNode;
import pit12.runtime.command.CommandRegistry;

public final class ForgeCommandAdapter extends CommandBase {
    private final CommandRegistry registry;
    private final CommandNode command;

    private ForgeCommandAdapter(CommandRegistry registry, CommandNode command) {
        this.registry = registry;
        this.command = command;
    }

    public static void register(CommandRegistry registry, CommandNode command) {
        ClientCommandHandler.instance.registerCommand(new ForgeCommandAdapter(registry, command));
    }

    @Override
    public String getCommandName() {
        return command.name();
    }

    @Override
    public List<String> getCommandAliases() {
        return command.aliases();
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return registry.usage(command);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return command.canUse(sender);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        registry.execute(command, sender, args);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args,
            BlockPos position) {
        return registry.complete(command, sender, args);
    }
}
