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
package pit12.feature.swap;

import static pit12.runtime.languages.Languages.source;
import static pit12.shared.chat.ChatFeedback.reply;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import org.lwjgl.input.Keyboard;
import pit12.runtime.command.CommandNode;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.languages.Languages;
import pit12.shared.chat.ChatFeedback.Tone;

final class SwapCommand {
    private final Minecraft minecraft;
    private final BindingBook bindings;
    private final SwapConfig config;
    private final AutoSwapController automatic;
    private final Languages language;

    SwapCommand(Minecraft minecraft, BindingBook bindings, SwapConfig config,
            AutoSwapController automatic, Languages language) {
        this.language = language;
        this.minecraft = minecraft;
        this.bindings = bindings;
        this.config = config;
        this.automatic = automatic;
    }

    CommandNode definition() {
        return CommandNode.command("swap", source("Manage swap bindings"))
                .child(operation("bind", source("Bind held armor or a hotbar item"), this::bind)
                        .arguments("<key> [slot]", 1, 2).suggests(this::bindSuggestions))
                .child(operation("unbind", source("Remove key or held item bindings"), this::unbind)
                        .arguments("[key]", 0, 1).suggests(this::unbindSuggestions))
                .child(operation("list", source("Show swap bindings"), this::list))
                .child(operation("clear", source("Clear swap bindings"), (sender, args) -> {
                    bindings.clear();
                    if (config.bindingMessages.get())
                        reply(sender, Tone.SUCCESS, language.translate("Cleared swap bindings"));
                })).child(operation("status", source("Show automatic swap state"), this::status))
                .child(operation("reset", source("Reset automatic swap state"), (sender, args) -> {
                    automatic.manualReset();
                    reply(sender, Tone.SUCCESS, language.translate("Reset automatic swap state"));
                })).build();
    }

    private CommandNode.Builder operation(String name, String description,
            CommandNode.Handler handler) {
        return CommandNode.command(name, description).executes((sender, args) -> {
            try {
                handler.execute(sender, args);
            } catch (IllegalArgumentException failure) {
                // Binding operations use IllegalArgumentException for input and readiness failures.
                reply(sender, Tone.ERROR, language.translate(failure.getMessage()));
            }
        });
    }

    private void bind(ICommandSender sender, String[] args) {
        requirePlayer();
        int target = 0;
        if (args.length == 2) {
            try {
                target = Integer.parseInt(args[1]);
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException(source("Hotbar slot must be from 1 to 9"));
            }
            if (target < 1 || target > 9) {
                throw new IllegalArgumentException(source("Hotbar slot must be from 1 to 9"));
            }
        }
        SwapBinding binding =
                SwapBinding.create(key(args[0]), minecraft.thePlayer.getHeldItem(), target);
        bindings.bind(binding);
        if (config.bindingMessages.get())
            reply(sender, Tone.SUCCESS,
                    config.messageDetails.get()
                            ? language.format("Bound {0} to {1} ({2})", binding.display(true),
                                    Keyboard.getKeyName(binding.key), binding.targetName())
                            : language.format("Bound {0} ({1})", Keyboard.getKeyName(binding.key),
                                    binding.targetName()));
    }

    private void unbind(ICommandSender sender, String[] args) {
        ItemIdentity held = null;
        if (args.length == 0) {
            requirePlayer();
            held = ItemIdentity.read(minecraft.thePlayer.getHeldItem());
            if (held == null)
                throw new IllegalArgumentException(source("Hold an item to unbind"));
        }
        int removed = bindings.unbind(args.length == 1 ? key(args[0]) : 0, held);
        if (removed == 0 || config.bindingMessages.get())
            reply(sender, removed == 0 ? Tone.WARNING : Tone.SUCCESS,
                    removed == 0 ? language.translate("No matching bindings")
                            : language.format("Removed {0} binding(s)", removed));
    }

    private void list(ICommandSender sender, String[] args) {
        if (bindings.readinessProblem() != null)
            throw new IllegalArgumentException(bindings.readinessProblem());
        reply(sender, Tone.INFO, language.format("Swap bindings: {0}", bindings.count()));
        for (SwapBinding entry : bindings.entries())
            reply(sender, Tone.INFO, Keyboard.getKeyName(entry.key) + ": "
                    + entry.display(config.messageDetails.get()) + " -> " + entry.targetName());
    }

    private void status(ICommandSender sender, String[] args) {
        reply(sender, Tone.INFO,
                language.format("Automatic swap: {0}",
                        config.autoSwap.get() ? language.translate("enabled")
                                : language.translate("disabled")));
        reply(sender, Tone.INFO, language.format("Escape Pod: {0}",
                state(PitEnchantment.Escape_Pod, config.escapePod.get())));
        reply(sender, Tone.INFO, language.format("Phoenix: {0}",
                state(PitEnchantment.Phoenix, config.phoenix.get())));
    }

    private void requirePlayer() {
        if (minecraft.thePlayer == null)
            throw new IllegalArgumentException(source("Join a world first"));
    }

    private String state(PitEnchantment type, boolean enabled) {
        if (!enabled)
            return language.translate("disabled");
        if (automatic.used(type))
            return language.translate("used");
        if (automatic.active(type))
            return language.translate("equipped");
        return language.translate("unused");
    }

    private int key(String name) {
        int code = Keyboard.getKeyIndex(name.toUpperCase(Locale.ROOT));
        if (code == Keyboard.KEY_NONE)
            throw new IllegalArgumentException(language.format("Unknown keyboard key: {0}", name));
        return code;
    }

    private List<String> bindSuggestions(ICommandSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9");
        }
        List<String> keys = new ArrayList<>();
        for (int code = 1; code < Keyboard.KEYBOARD_SIZE; code++) {
            String name = Keyboard.getKeyName(code);
            if (name != null)
                keys.add(name);
        }
        return keys;
    }

    private List<String> unbindSuggestions(ICommandSender sender, String[] args) {
        List<String> keys = new ArrayList<>();
        for (SwapBinding entry : bindings.entries())
            keys.add(Keyboard.getKeyName(entry.key));
        return keys;
    }
}
