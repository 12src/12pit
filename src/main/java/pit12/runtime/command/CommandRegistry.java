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
package pit12.runtime.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.lifecycle.ClientLifecycle;

public final class CommandRegistry extends CommandBase implements ClientLifecycle {
    private final ClientThread client;
    private final Map<String, ICommand> commands = new LinkedHashMap<>();
    private final List<ICommand> standaloneCommands = new ArrayList<>();
    private boolean registered;
    private boolean started;

    public CommandRegistry(ClientThread client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public void register(ICommand command) {
        register(command, false);
    }

    public void register(ICommand command, boolean standalone) {
        client.check();
        if (registered) {
            throw new IllegalStateException("Commands must be registered before startup");
        }
        Objects.requireNonNull(command, "command");
        List<String> names = new ArrayList<>();
        names.add(command.getCommandName());
        names.addAll(command.getCommandAliases());
        for (String name : names) {
            if (name == null || !name.matches("[a-z0-9_-]+") || getCommandName().equals(name)) {
                throw new IllegalArgumentException("Invalid command name: " + name);
            }
            if (commands.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate command name: " + name);
            }
        }
        for (String name : names) {
            commands.put(name, command);
        }
        if (standalone) {
            standaloneCommands.add(command);
        }
    }

    @Override
    public void start() {
        client.check();
        if (started) {
            return;
        }
        if (!registered) {
            // Forge has no command unregister API, so keep registrations across restarts.
            ClientCommandHandler.instance.registerCommand(this);
            for (ICommand command : standaloneCommands) {
                ClientCommandHandler.instance.registerCommand(command);
            }
            registered = true;
        }
        started = true;
    }

    @Override
    public void stop() {
        client.check();
        started = false;
    }

    @Override
    public String getCommandName() {
        return "12pit";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/12pit <" + String.join("|", commands.keySet()) + ">";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        client.check();
        if (!started) {
            reply(sender, "Commands are unavailable");
            return;
        }
        ICommand command = args.length == 0 ? null : commands.get(args[0].toLowerCase(Locale.ROOT));
        if (command == null) {
            reply(sender, getCommandUsage(sender));
            return;
        }
        if (!command.canCommandSenderUseCommand(sender)) {
            throw new CommandException("commands.generic.permission");
        }
        command.processCommand(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args,
            BlockPos position) {
        client.check();
        if (!started || args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            for (Map.Entry<String, ICommand> entry : commands.entrySet()) {
                if (entry.getValue().canCommandSenderUseCommand(sender)) {
                    options.add(entry.getKey());
                }
            }
            return getListOfStringsMatchingLastWord(args, options);
        }
        ICommand command = commands.get(args[0].toLowerCase(Locale.ROOT));
        if (command == null || !command.canCommandSenderUseCommand(sender)) {
            return Collections.emptyList();
        }
        return command.addTabCompletionOptions(sender, Arrays.copyOfRange(args, 1, args.length),
                position);
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        if (args.length < 2 || index < 1) {
            return false;
        }
        ICommand command = commands.get(args[0].toLowerCase(Locale.ROOT));
        return command != null
                && command.isUsernameIndex(Arrays.copyOfRange(args, 1, args.length), index - 1);
    }

    private static void reply(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(
                EnumChatFormatting.AQUA + "[12pit]" + EnumChatFormatting.RESET + " " + message));
    }
}
