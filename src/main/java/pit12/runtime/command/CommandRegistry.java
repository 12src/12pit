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

import static pit12.runtime.languages.Languages.source;
import static pit12.shared.chat.ChatFeedback.reply;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import pit12.runtime.languages.Languages;
import pit12.shared.chat.ChatFeedback.Tone;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.lifecycle.ClientLifecycle;

public final class CommandRegistry implements ClientLifecycle {
    private final ClientThread client;
    private final Languages language;
    private final Registrar registrar;
    private final CommandNode.Builder rootBuilder =
            CommandNode.command("12pit", source("12pit commands"));
    private final List<CommandNode> entryPoints = new ArrayList<>();
    private CommandNode root;
    private int installed;
    private boolean started;

    public CommandRegistry(ClientThread client, Registrar registrar, Languages language) {
        this.language = language;
        this.client = client;
        this.registrar = registrar;
    }

    public void register(CommandNode command) {
        register(command, false);
    }

    public void register(CommandNode command, boolean standalone) {
        client.check();
        if (root != null) {
            throw new IllegalStateException("Commands must be registered before startup");
        }
        if ("12pit".equals(command.name) || command.aliases.contains("12pit")) {
            throw new IllegalArgumentException("The command name 12pit is reserved");
        }
        rootBuilder.child(command);
        if (standalone) {
            entryPoints.add(command);
        }
    }

    @Override
    public void start() {
        client.check();
        if (started) {
            return;
        }
        if (root == null) {
            root = rootBuilder.build();
            entryPoints.add(0, root);
        }
        // Forge cannot unregister commands. Reuse installed entry points after a restart or partial startup.
        while (installed < entryPoints.size()) {
            registrar.register(this, entryPoints.get(installed));
            installed++;
        }
        started = true;
    }

    @Override
    public void stop() {
        client.check();
        started = false;
    }

    public String usage(CommandNode command) {
        return usage(command, "/" + command.name);
    }

    public void execute(CommandNode command, ICommandSender sender, String[] args)
            throws CommandException {
        client.check();
        if (!started) {
            reply(sender, Tone.WARNING, language.translate("Commands are unavailable"));
            return;
        }
        execute(command, sender, args, "/" + command.name);
    }

    private void execute(CommandNode command, ICommandSender sender, String[] args, String path)
            throws CommandException {
        if (!command.canUse(sender)) {
            throw new CommandException("commands.generic.permission");
        }
        if (args.length == 0 && (!command.children.isEmpty() || command.handler == null)) {
            help(command, sender, path);
            return;
        }
        if (args.length > 0) {
            CommandNode child = command.childNames.get(args[0].toLowerCase(Locale.ROOT));
            if (child != null) {
                execute(child, sender, Arrays.copyOfRange(args, 1, args.length),
                        path + " " + args[0]);
                return;
            }
        }
        if (!command.children.isEmpty() && args.length == 1 && "help".equalsIgnoreCase(args[0])) {
            help(command, sender, path);
            return;
        }
        if (command.handler == null) {
            reply(sender, Tone.ERROR, language.format("Unknown command: {0}", args[0]));
            help(command, sender, path);
        } else if (args.length < command.minimumArguments
                || args.length > command.maximumArguments) {
            reply(sender, Tone.WARNING, language.format("Usage: {0}",
                    path + (command.arguments.isEmpty() ? "" : " " + command.arguments)));
        } else {
            command.handler.execute(sender, args);
        }
    }

    public List<String> complete(CommandNode command, ICommandSender sender, String[] args) {
        client.check();
        if (!started || args.length == 0 || !command.canUse(sender)) {
            return Collections.emptyList();
        }
        if (args.length > 1) {
            CommandNode child = command.childNames.get(args[0].toLowerCase(Locale.ROOT));
            if (child != null) {
                return complete(child, sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }
        Set<String> options = new LinkedHashSet<>();
        if (args.length == 1) {
            for (CommandNode child : command.children) {
                if (child.canUse(sender)) {
                    options.add(child.name);
                    options.addAll(child.aliases);
                }
            }
        }
        if (command.suggestions != null && args.length <= command.maximumArguments) {
            List<String> suggestions = command.suggestions.suggest(sender, args);
            if (suggestions != null) {
                options.addAll(suggestions);
            }
        }
        List<String> matches = new ArrayList<>();
        String prefix = args[args.length - 1];
        for (String option : options) {
            if (option != null && option.regionMatches(true, 0, prefix, 0, prefix.length())) {
                matches.add(option);
            }
        }
        return matches;
    }

    private static String usage(CommandNode command, String path) {
        if (!command.children.isEmpty()) {
            return path + " <command>";
        }
        return path + (command.arguments.isEmpty() ? "" : " " + command.arguments);
    }

    private void help(CommandNode command, ICommandSender sender, String path) {
        reply(sender, Tone.INFO, path + " - " + language.translate(command.description));
        for (CommandNode child : command.children) {
            if (child.canUse(sender)) {
                reply(sender, Tone.INFO, usage(child, path + " " + child.name) + " - "
                        + language.translate(child.description));
            }
        }
        if (command.handler != null && !command.arguments.isEmpty()) {
            reply(sender, Tone.INFO, path + " " + command.arguments + " - "
                    + language.translate(command.description));
        }
    }

    @FunctionalInterface
    public interface Registrar {
        void register(CommandRegistry registry, CommandNode command);
    }
}
