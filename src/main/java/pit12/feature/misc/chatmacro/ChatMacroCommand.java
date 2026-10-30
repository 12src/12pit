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
package pit12.feature.misc.chatmacro;

import static pit12.runtime.languages.Languages.source;
import static pit12.shared.chat.ChatFeedback.reply;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.command.ICommandSender;
import org.lwjgl.input.Keyboard;
import pit12.runtime.command.CommandNode;
import pit12.runtime.languages.Languages;
import pit12.shared.chat.ChatFeedback.Tone;

final class ChatMacroCommand {
    private final MacroBindings bindings;
    private final Languages language;

    ChatMacroCommand(MacroBindings bindings, Languages language) {
        this.bindings = bindings;
        this.language = language;
    }

    CommandNode definition() {
        return CommandNode.command("macro", source("Manage chat macros"))
                .child(operation("bind", source("Bind a chat message to a key"), this::bind)
                        .arguments("<key> <message>", 2, Integer.MAX_VALUE)
                        .suggests(this::bindSuggestions))
                .child(operation("list", source("Show chat macros"), this::list))
                .child(operation("remove", source("Remove a chat macro"), this::remove)
                        .aliases("unbind", "delete").arguments("<key>", 1, 1)
                        .suggests(this::removeSuggestions))
                .child(operation("clear", source("Clear chat macros"), (sender, args) -> {
                    bindings.clear();
                    reply(sender, Tone.SUCCESS, language.translate("Cleared chat macros"));
                })).build();
    }

    private CommandNode.Builder operation(String name, String description,
            CommandNode.Handler handler) {
        return CommandNode.command(name, description).executes((sender, args) -> {
            try {
                handler.execute(sender, args);
            } catch (IllegalArgumentException failure) {
                reply(sender, Tone.ERROR, failure.getMessage());
            }
        });
    }

    private void bind(ICommandSender sender, String[] args) {
        int key = key(args[0]);
        bindings.bind(key, String.join(" ", Arrays.asList(args).subList(1, args.length)));
        reply(sender, Tone.SUCCESS,
                language.format("Bound chat macro to {0}", Keyboard.getKeyName(key)));
    }

    private void list(ICommandSender sender, String[] args) {
        bindings.requireReady();
        Map<Integer, String> entries = bindings.entries();
        reply(sender, Tone.INFO, language.format("Chat macros: {0}", entries.size()));
        for (Map.Entry<Integer, String> entry : entries.entrySet())
            reply(sender, Tone.INFO, Keyboard.getKeyName(entry.getKey()) + ": " + entry.getValue());
    }

    private void remove(ICommandSender sender, String[] args) {
        int key = key(args[0]);
        boolean removed = bindings.remove(key);
        reply(sender, removed ? Tone.SUCCESS : Tone.WARNING,
                removed ? language.format("Removed chat macro for {0}", Keyboard.getKeyName(key))
                        : language.translate("No matching chat macro"));
    }

    private int key(String name) {
        int key = Keyboard.getKeyIndex(name.toUpperCase(Locale.ROOT));
        if (key == Keyboard.KEY_NONE)
            throw new IllegalArgumentException("Unknown keyboard key: " + name);
        return key;
    }

    private List<String> bindSuggestions(ICommandSender sender, String[] args) {
        if (args.length != 1)
            return Collections.emptyList();
        List<String> keys = new ArrayList<>();
        for (int key = 1; key < Keyboard.KEYBOARD_SIZE; key++) {
            String name = Keyboard.getKeyName(key);
            if (name != null)
                keys.add(name);
        }
        return keys;
    }

    private List<String> removeSuggestions(ICommandSender sender, String[] args) {
        List<String> keys = new ArrayList<>();
        for (int key : bindings.entries().keySet())
            keys.add(Keyboard.getKeyName(key));
        return keys;
    }
}
