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

import static pit12.runtime.command.CommandRegistry.reply;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import org.lwjgl.input.Keyboard;
import pit12.runtime.command.CommandNode;
import pit12.runtime.item.PitEnchantment;

final class SwapCommand {
    private final Minecraft minecraft;
    private final BindingBook bindings;
    private final SwapConfig config;
    private final AutoSwapController automatic;

    SwapCommand(Minecraft minecraft, BindingBook bindings, SwapConfig config,
            AutoSwapController automatic) {
        this.minecraft = minecraft;
        this.bindings = bindings;
        this.config = config;
        this.automatic = automatic;
    }

    CommandNode definition() {
        return CommandNode.command("swap", "Manage swap bindings")
                .child(operation("bind", "Bind held armor or a hotbar item", this::bind)
                        .arguments("<key> [slot]", 1, 2).suggests(this::bindSuggestions))
                .child(operation("unbind", "Remove key or held item bindings", this::unbind)
                        .arguments("[key]", 0, 1).suggests(this::unbindSuggestions))
                .child(operation("list", "Show swap bindings", this::list))
                .child(operation("clear", "Clear swap bindings", (sender, args) -> {
                    bindings.clear();
                    if (config.bindingMessages.get())
                        reply(sender, "Cleared swap bindings");
                })).child(operation("status", "Show automatic swap state", this::status))
                .child(operation("reset", "Reset automatic swap state", (sender, args) -> {
                    automatic.manualReset();
                    reply(sender, "Reset automatic swap state");
                })).build();
    }

    private CommandNode.Builder operation(String name, String description,
            CommandNode.Handler handler) {
        return CommandNode.command(name, description).executes((sender, args) -> {
            try {
                handler.execute(sender, args);
            } catch (IllegalArgumentException failure) {
                // Binding operations use IllegalArgumentException for input and readiness failures.
                reply(sender, failure.getMessage());
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
                throw new IllegalArgumentException("Hotbar slot must be from 1 to 9");
            }
            if (target < 1 || target > 9) {
                throw new IllegalArgumentException("Hotbar slot must be from 1 to 9");
            }
        }
        SwapBinding binding =
                SwapBinding.create(key(args[0]), minecraft.thePlayer.getHeldItem(), target);
        bindings.bind(binding);
        if (config.bindingMessages.get())
            reply(sender,
                    "Bound " + (config.messageDetails.get() ? binding.display(true) + " to " : "")
                            + Keyboard.getKeyName(binding.key) + " (" + binding.targetName() + ")");
    }

    private void unbind(ICommandSender sender, String[] args) {
        ItemIdentity held = null;
        if (args.length == 0) {
            requirePlayer();
            held = ItemIdentity.read(minecraft.thePlayer.getHeldItem());
            if (held == null)
                throw new IllegalArgumentException("Hold an item to unbind");
        }
        int removed = bindings.unbind(args.length == 1 ? key(args[0]) : 0, held);
        if (removed == 0 || config.bindingMessages.get())
            reply(sender,
                    removed == 0 ? "No matching bindings" : "Removed " + removed + " binding(s)");
    }

    private void list(ICommandSender sender, String[] args) {
        if (bindings.readinessProblem() != null)
            throw new IllegalArgumentException(bindings.readinessProblem());
        reply(sender, "Swap bindings: " + bindings.count());
        for (SwapBinding entry : bindings.entries())
            reply(sender, Keyboard.getKeyName(entry.key) + ": "
                    + entry.display(config.messageDetails.get()) + " -> " + entry.targetName());
    }

    private void status(ICommandSender sender, String[] args) {
        reply(sender, "Automatic swap: " + (config.autoSwap.get() ? "enabled" : "disabled"));
        reply(sender, "Escape Pod: " + state(PitEnchantment.Escape_Pod, config.escapePod.get()));
        reply(sender, "Phoenix: " + state(PitEnchantment.Phoenix, config.phoenix.get()));
    }

    private void requirePlayer() {
        if (minecraft.thePlayer == null)
            throw new IllegalArgumentException("Join a world first");
    }

    private String state(PitEnchantment type, boolean enabled) {
        if (!enabled)
            return "disabled";
        if (automatic.used(type))
            return "used";
        if (automatic.active(type))
            return "equipped";
        return "unused";
    }

    private static int key(String name) {
        int code = Keyboard.getKeyIndex(name.toUpperCase(Locale.ROOT));
        if (code == Keyboard.KEY_NONE)
            throw new IllegalArgumentException("Unknown keyboard key: " + name);
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
        for (SwapBinding entry : bindings.entries()) {
            String name = Keyboard.getKeyName(entry.key);
            if (name != null)
                keys.add(name);
        }
        return keys;
    }
}
