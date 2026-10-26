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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.command.CommandHandler;
import pit12.platform.mixin.accessor.CommandHandlerAccessor;
import pit12.runtime.command.CommandNode;
import pit12.runtime.command.CommandRegistry;

public final class ClientCommandAdapter extends CommandBase {
    private final CommandRegistry registry;
    private final CommandNode command;

    private ClientCommandAdapter(CommandRegistry registry, CommandNode command) {
        this.registry = registry;
        this.command = command;
    }

    public static Runnable register(CommandHandler handler, CommandRegistry registry, CommandNode command) {
        ClientCommandAdapter adapter = new ClientCommandAdapter(registry, command);
        List<String> names = new ArrayList<>(command.aliases());
        names.add(command.name());
        Map<String, ICommand> previous = new LinkedHashMap<>();
        Map<String, ICommand> commands = handler.getCommands();
        for (String name : names) {
            previous.put(name, commands.get(name));
        }
        handler.registerCommand(adapter);
        return () -> {
            for (Map.Entry<String, ICommand> entry : previous.entrySet()) {
                // Someone may have taken this name since. Leave theirs alone.
                if (commands.get(entry.getKey()) == adapter) {
                    if (entry.getValue() == null) {
                        commands.remove(entry.getKey());
                    } else {
                        commands.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            // registerCommand also keeps the command in this set.
            ((CommandHandlerAccessor) handler).pit12$getCommandSet().remove(adapter);
        };
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
        return registry.available(command) && command.canUse(sender);
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
