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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;

public final class CommandNode {
    final String name;
    final String description;
    final String arguments;
    final int minimumArguments;
    final int maximumArguments;
    final List<String> aliases;
    final List<CommandNode> children;
    final Map<String, CommandNode> childNames;
    final Handler handler;
    final Suggestions suggestions;
    private final Predicate<ICommandSender> requirement;

    private CommandNode(Builder builder) {
        name = builder.name;
        description = builder.description;
        arguments = builder.arguments;
        minimumArguments = builder.minimumArguments;
        maximumArguments = builder.maximumArguments;
        aliases = Collections.unmodifiableList(new ArrayList<>(builder.aliases));
        children = Collections.unmodifiableList(new ArrayList<>(builder.children));
        childNames = Collections.unmodifiableMap(new LinkedHashMap<>(builder.childNames));
        handler = builder.handler;
        suggestions = builder.suggestions;
        requirement = builder.requirement;
    }

    public static Builder command(String name, String description) {
        return new Builder(name, description);
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public boolean canUse(ICommandSender sender) {
        return requirement.test(sender);
    }

    @FunctionalInterface
    public interface Handler {
        /** Arguments exclude the command path. Their count is checked before execution. */
        void execute(ICommandSender sender, String[] args) throws CommandException;
    }
    @FunctionalInterface
    public interface Suggestions {
        /** Arguments include the current word. Return unfiltered candidates, or null for no suggestions. */
        List<String> suggest(ICommandSender sender, String[] args);
    }
    public static final class Builder {
        private final String name;
        private final String description;
        private final List<String> aliases = new ArrayList<>();
        private final List<CommandNode> children = new ArrayList<>();
        private final Map<String, CommandNode> childNames = new LinkedHashMap<>();
        private String arguments = "";
        private int minimumArguments;
        private int maximumArguments;
        private Handler handler;
        private Suggestions suggestions;
        private Predicate<ICommandSender> requirement = sender -> true;

        private Builder(String name, String description) {
            checkName(name);
            this.name = name;
            this.description = Objects.requireNonNull(description, "description");
            if (description.trim().isEmpty()) {
                throw new IllegalArgumentException("Command description must not be empty");
            }
        }

        public Builder aliases(String... names) {
            for (String alias : names) {
                checkName(alias);
                if (name.equals(alias) || aliases.contains(alias)) {
                    throw new IllegalArgumentException("Duplicate command name: " + alias);
                }
                aliases.add(alias);
            }
            return this;
        }

        public Builder arguments(String usage, int minimum, int maximum) {
            Objects.requireNonNull(usage, "usage");
            if (usage.trim().isEmpty() || minimum < 0 || maximum < minimum) {
                throw new IllegalArgumentException("Invalid command arguments");
            }
            arguments = usage;
            minimumArguments = minimum;
            maximumArguments = maximum;
            return this;
        }

        /** Groups use this handler for unmatched arguments and show help when called without arguments. */
        public Builder executes(Handler handler) {
            this.handler = Objects.requireNonNull(handler, "handler");
            return this;
        }

        public Builder suggests(Suggestions suggestions) {
            this.suggestions = Objects.requireNonNull(suggestions, "suggestions");
            return this;
        }

        public Builder requires(Predicate<ICommandSender> requirement) {
            this.requirement = Objects.requireNonNull(requirement, "requirement");
            return this;
        }

        public Builder child(Builder child) {
            return child(Objects.requireNonNull(child, "child").build());
        }

        public Builder child(CommandNode child) {
            Objects.requireNonNull(child, "child");
            List<String> names = new ArrayList<>(child.aliases);
            names.add(child.name);
            for (String childName : names) {
                if (childNames.containsKey(childName)) {
                    throw new IllegalArgumentException("Duplicate command name: " + childName);
                }
            }
            for (String childName : names) {
                childNames.put(childName, child);
            }
            children.add(child);
            return this;
        }

        public CommandNode build() {
            return new CommandNode(this);
        }

        private static void checkName(String name) {
            if (name == null || !name.matches("[a-z0-9_-]+")) {
                throw new IllegalArgumentException("Invalid command name: " + name);
            }
        }
    }
}
