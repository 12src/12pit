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

import static net.minecraft.util.EnumChatFormatting.AQUA;
import static net.minecraft.util.EnumChatFormatting.WHITE;
import static pit12.runtime.languages.Languages.source;
import static pit12.shared.chat.ChatFeedback.reply;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandNotFoundException;
import net.minecraft.command.ICommandSender;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.languages.Languages;
import pit12.shared.chat.ChatFeedback.Tone;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.lifecycle.ClientLifecycle;

public final class CommandRegistry implements ClientLifecycle, ConfigChangeListener {
    private final ClientThread client;
    private final ConfigCatalog configs;
    private final Languages language;
    private final Registrar registrar;
    private final CommandNode.Builder rootBuilder =
            CommandNode.command("12pit", source("12pit commands"));
    private final List<CommandNode> entryPoints = new ArrayList<>();
    private CommandNode root;
    private final Map<CommandNode, FeatureConfig> owners = new LinkedHashMap<>();
    private final Set<FeatureConfig> activeOwners = new LinkedHashSet<>();
    private final Map<CommandNode, Runnable> installed = new LinkedHashMap<>();
    private boolean started;

    public CommandRegistry(ClientThread client, ConfigCatalog configs, Registrar registrar,
            Languages language) {
        this.language = language;
        this.client = client;
        this.configs = configs;
        this.registrar = registrar;
    }

    public void register(CommandNode command) {
        register(command, false);
    }

    public void register(CommandNode command, boolean standalone) {
        register(command, null, standalone);
    }

    public void register(CommandNode command, FeatureConfig owner, boolean standalone) {
        client.check();
        if (root != null) {
            throw new IllegalStateException("Commands must be registered before startup");
        }
        if ("12pit".equals(command.name) || command.aliases.contains("12pit")) {
            throw new IllegalArgumentException("The command name 12pit is reserved");
        }
        rootBuilder.child(command);
        if (owner != null) {
            owners.put(command, owner);
        }
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
        started = true;
        configs.addListener(this);
        updateRegistrations();
    }

    @Override
    public void stop() {
        client.check();
        started = false;
        configs.removeListener(this);
        for (Runnable unregister : installed.values()) {
            unregister.run();
        }
        installed.clear();
    }

    public boolean available(CommandNode command) {
        client.check();
        FeatureConfig owner = owners.get(command);
        return started && (owner == null || (activeOwners.contains(owner) && owner.enabled()));
    }

    public void setActive(FeatureConfig owner, boolean active) {
        client.check();
        if (active) {
            activeOwners.add(owner);
        } else {
            activeOwners.remove(owner);
        }
        updateRegistrations();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        for (FeatureConfig owner : owners.values()) {
            if (changes.affects(owner.id(), "enabled")) {
                updateRegistrations();
                return;
            }
        }
    }

    private void updateRegistrations() {
        for (CommandNode command : entryPoints) {
            if (available(command)) {
                if (!installed.containsKey(command)) {
                    installed.put(command, registrar.register(this, command));
                }
            } else {
                Runnable unregister = installed.remove(command);
                if (unregister != null) {
                    unregister.run();
                }
            }
        }
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
        if (!available(command)) {
            throw new CommandNotFoundException();
        }
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
            reply(sender, Tone.ERROR,
                    language.format("Unknown command: {0}", Tone.ERROR.argument(WHITE, args[0])));
            help(command, sender, path);
        } else if (args.length < command.minimumArguments
                || args.length > command.maximumArguments) {
            reply(sender, Tone.WARNING, language.format("Usage: {0}", Tone.WARNING.argument(AQUA,
                    path + (command.arguments.isEmpty() ? "" : " " + command.arguments))));
        } else {
            command.handler.execute(sender, args);
        }
    }

    public List<String> complete(CommandNode command, ICommandSender sender, String[] args) {
        client.check();
        if (!available(command) || args.length == 0 || !command.canUse(sender)) {
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
                if (available(child) && child.canUse(sender)) {
                    options.add(child.name);
                }
            }
        }
        if (command.suggestions != null && args.length <= command.maximumArguments) {
            options.addAll(command.suggestions.suggest(sender, args));
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
        reply(sender, Tone.INFO, Tone.INFO.argument(AQUA, path) + " - "
                + Tone.INFO.argument(WHITE, language.translate(command.description)));
        for (CommandNode child : command.children) {
            if (available(child) && child.canUse(sender)) {
                String aliases = child.aliases.isEmpty() ? ""
                        : language.format(" (aliases: {0})",
                                Tone.INFO.argument(AQUA, String.join(", ", child.aliases)));
                reply(sender, Tone.INFO,
                        Tone.INFO.argument(AQUA, usage(child, path + " " + child.name)) + aliases
                                + " - "
                                + Tone.INFO.argument(WHITE, language.translate(child.description)));
            }
        }
        if (command.handler != null && !command.arguments.isEmpty()) {
            reply(sender, Tone.INFO, Tone.INFO.argument(AQUA, path + " " + command.arguments)
                    + " - " + Tone.INFO.argument(WHITE, language.translate(command.description)));
        }
    }

    @FunctionalInterface
    public interface Registrar {
        Runnable register(CommandRegistry registry, CommandNode command);
    }
}
