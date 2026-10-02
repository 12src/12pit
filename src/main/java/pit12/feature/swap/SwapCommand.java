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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;
import pit12.runtime.item.PitEnchantment;

final class SwapCommand extends CommandBase {
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

    @Override
    public String getCommandName() {
        return "swap";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/swap <bind|unbind|list|clear|status|reset|help> or /12pit swap <...>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            String operation = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
            switch (operation) {
                case "bind":
                    if (args.length < 2 || args.length > 3)
                        throw new IllegalArgumentException("Usage: /swap bind <key> [slot]");
                    requirePlayer();
                    int target = args.length == 3 ? Integer.parseInt(args[2]) : 0;
                    if (args.length == 3 && (target < 1 || target > 9)) {
                        throw new IllegalArgumentException("Hotbar slot must be from 1 to 9");
                    }
                    SwapBinding binding = SwapBinding.create(key(args[1]),
                            minecraft.thePlayer.getHeldItem(), target);
                    bindings.bind(binding);
                    if (config.bindingMessages.get())
                        reply(sender, "Bound "
                                + (config.messageDetails.get() ? binding.display(true) + " to "
                                        : "")
                                + Keyboard.getKeyName(binding.key) + " (" + binding.targetName()
                                + ")");
                    break;
                case "unbind":
                    if (args.length > 2)
                        throw new IllegalArgumentException("Usage: /swap unbind [key]");
                    ItemIdentity held = null;
                    if (args.length == 1) {
                        requirePlayer();
                        held = ItemIdentity.read(minecraft.thePlayer.getHeldItem());
                        if (held == null)
                            throw new IllegalArgumentException("Hold an item to unbind");
                    }
                    int removed = bindings.unbind(args.length == 2 ? key(args[1]) : 0, held);
                    if (removed == 0 || config.bindingMessages.get())
                        reply(sender, removed == 0 ? "No matching bindings"
                                : "Removed " + removed + " binding(s)");
                    break;
                case "clear":
                    if (args.length != 1)
                        throw new IllegalArgumentException("Usage: /swap clear");
                    bindings.clear();
                    if (config.bindingMessages.get())
                        reply(sender, "Cleared swap bindings");
                    break;
                case "list":
                    if (args.length != 1)
                        throw new IllegalArgumentException("Usage: /swap list");
                    if (bindings.readinessProblem() != null)
                        throw new IllegalArgumentException(bindings.readinessProblem());
                    reply(sender, "Swap bindings: " + bindings.count());
                    for (SwapBinding entry : bindings.entries())
                        reply(sender,
                                Keyboard.getKeyName(entry.key) + ": "
                                        + entry.display(config.messageDetails.get()) + " -> "
                                        + entry.targetName());
                    break;
                case "status":
                    if (args.length != 1)
                        throw new IllegalArgumentException("Usage: /swap status");
                    reply(sender,
                            "Automatic swap: " + (config.autoSwap.get() ? "enabled" : "disabled"));
                    reply(sender, "Escape Pod: "
                            + state(PitEnchantment.Escape_Pod, config.escapePod.get()));
                    reply(sender,
                            "Phoenix: " + state(PitEnchantment.Phoenix, config.phoenix.get()));
                    break;
                case "reset":
                    if (args.length != 1)
                        throw new IllegalArgumentException("Usage: /swap reset");
                    automatic.manualReset();
                    reply(sender, "Reset automatic swap state");
                    break;
                case "help":
                    if (args.length > 1)
                        throw new IllegalArgumentException("Usage: /swap help");
                    reply(sender, "/swap bind <key> [slot] - bind held armor or a hotbar item");
                    reply(sender, "/swap unbind [key] - remove key or held item bindings");
                    reply(sender, "/swap list | clear | status | reset | help");
                    reply(sender, "All commands also work under /12pit swap");
                    break;
                default:
                    throw new IllegalArgumentException(getCommandUsage(sender));
            }
        } catch (IllegalArgumentException failure) {
            reply(sender,
                    failure instanceof NumberFormatException ? "Hotbar slot must be from 1 to 9"
                            : failure.getMessage());
        }
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

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args,
            BlockPos pos) {
        if (args.length == 1)
            return getListOfStringsMatchingLastWord(args, "bind", "unbind", "list", "clear",
                    "status", "reset", "help");
        if (args.length == 2 && "bind".equalsIgnoreCase(args[0])) {
            List<String> keys = new ArrayList<>();
            for (int code = 1; code < Keyboard.KEYBOARD_SIZE; code++) {
                String name = Keyboard.getKeyName(code);
                if (name != null)
                    keys.add(name);
            }
            return getListOfStringsMatchingLastWord(args, keys);
        }
        if (args.length == 2 && "unbind".equalsIgnoreCase(args[0])) {
            List<String> keys = new ArrayList<>();
            for (SwapBinding entry : bindings.entries()) {
                String name = Keyboard.getKeyName(entry.key);
                if (name != null && !keys.contains(name))
                    keys.add(name);
            }
            return getListOfStringsMatchingLastWord(args, keys);
        }
        if (args.length == 3 && "bind".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args,
                    Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9"));
        }
        return Collections.emptyList();
    }

    static void reply(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText("\u00a7b[12pit]\u00a7r " + message));
    }
}
