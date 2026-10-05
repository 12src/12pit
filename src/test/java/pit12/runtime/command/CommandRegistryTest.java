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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.IChatComponent;
import org.junit.Test;
import pit12.runtime.languages.Languages;
import pit12.shared.concurrent.ClientThread;

public final class CommandRegistryTest {
    private final List<String> messages = new ArrayList<>();
    private final List<CommandNode> installed = new ArrayList<>();
    private final ICommandSender sender =
            (ICommandSender) Proxy.newProxyInstance(ICommandSender.class.getClassLoader(),
                    new Class<?>[] {ICommandSender.class}, (proxy, method, args) -> {
                        if ("addChatMessage".equals(method.getName())) {
                            messages.add(((IChatComponent) args[0]).getUnformattedText());
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
    private final CommandRegistry registry = new CommandRegistry(ClientThread.current(),
            (owner, command) -> installed.add(command), new Languages(ClientThread.current()));

    @Test
    public void groupsShowHelpAndLeavesExecuteFromBothEntryPoints() throws Exception {
        List<String[]> calls = new ArrayList<>();
        CommandNode swap = CommandNode.command("swap", "Manage bindings")
                .child(CommandNode.command("bind", "Bind an item").arguments("<key> [slot]", 1, 2)
                        .executes((source, args) -> calls.add(args)))
                .build();
        registry.register(swap, true);
        registry.start();
        registry.execute(installed.get(0), sender, new String[0]);
        assertTrue(hasMessage("/12pit swap <command> - Manage bindings"));
        messages.clear();
        registry.execute(installed.get(0), sender, new String[] {"swap"});
        assertTrue(hasMessage("/12pit swap bind <key> [slot] - Bind an item"));
        registry.execute(swap, sender, new String[] {"help"});
        assertTrue(hasMessage("/swap bind <key> [slot] - Bind an item"));
        assertTrue(calls.isEmpty());
        registry.execute(installed.get(0), sender, new String[] {"SWAP", "bind", "R", "2"});
        registry.execute(swap, sender, new String[] {"bind", "T"});
        assertEquals(2, calls.size());
        assertArrayEquals(new String[] {"R", "2"}, calls.get(0));
        assertArrayEquals(new String[] {"T"}, calls.get(1));
    }

    @Test
    public void leavesRunWithoutHelpAndInvalidCountsUseTheCurrentPath() throws Exception {
        List<String[]> calls = new ArrayList<>();
        CommandNode editor = CommandNode.command("hudeditor", "Open the editor")
                .executes((source, args) -> calls.add(args)).build();
        registry.register(editor, true);
        registry.start();
        registry.execute(installed.get(0), sender, new String[] {"hudeditor"});
        registry.execute(editor, sender, new String[0]);
        assertEquals(2, calls.size());
        assertTrue(messages.isEmpty());
        registry.execute(installed.get(0), sender, new String[] {"hudeditor", "extra"});
        registry.execute(editor, sender, new String[] {"extra"});
        assertEquals(2, calls.size());
        assertTrue(hasMessage("Usage: /12pit hudeditor"));
        assertTrue(hasMessage("Usage: /hudeditor"));
    }

    @Test
    public void completionCombinesNamesAliasesAndArgumentSuggestions() {
        CommandNode group = CommandNode.command("group", "Manage players").aliases("g")
                .arguments("<player>", 1, 1).executes((source, args) -> {
                }).suggests((source, args) -> Arrays.asList("Alice", "Alice"))
                .child(CommandNode.command("remove", "Remove a player").aliases("rm")
                        .arguments("<player>", 1, 1).executes((source, args) -> {
                        }).suggests((source, args) -> Arrays.asList("Alice", "Bob", "Alice")))
                .child(CommandNode.command("secret", "Hidden action").requires(source -> false))
                .build();
        registry.register(group);
        registry.start();
        assertEquals(Arrays.asList("group", "g"),
                registry.complete(installed.get(0), sender, new String[] {""}));
        assertEquals(Arrays.asList("remove", "rm", "Alice"),
                registry.complete(installed.get(0), sender, new String[] {"g", ""}));
        assertEquals(Collections.singletonList("Alice"),
                registry.complete(installed.get(0), sender, new String[] {"g", "rm", "a"}));
        assertTrue(registry.complete(group, sender, new String[] {"secret", ""}).isEmpty());
        assertTrue(registry.complete(group, sender, new String[] {"rm", "Alice", ""}).isEmpty());
    }

    @Test
    public void childCommandsTakePriorityOverThePlayerFallback() throws Exception {
        List<String> calls = new ArrayList<>();
        CommandNode friends = CommandNode.command("friend", "Manage friends")
                .arguments("<player>", 1, 1).executes((source, args) -> calls.add(args[0]))
                .child(CommandNode.command("list", "Show friends")
                        .executes((source, args) -> calls.add("listed")))
                .build();
        registry.register(friends);
        registry.start();
        registry.execute(friends, sender, new String[0]);
        assertTrue(calls.isEmpty());
        registry.execute(friends, sender, new String[] {"list"});
        registry.execute(friends, sender, new String[] {"Alice"});
        registry.execute(friends, sender, new String[] {"add", "Alice"});
        assertEquals(Arrays.asList("listed", "Alice"), calls);
        assertTrue(hasMessage("Usage: /friend <player>"));
    }

    @Test
    public void deniedCommandsAreHiddenAndCannotExecute() throws Exception {
        CommandNode hidden =
                CommandNode.command("hidden", "Hidden action").requires(source -> false)
                        .executes((source, args) -> fail("Executed hidden command")).build();
        registry.register(hidden, true);
        registry.start();
        registry.execute(installed.get(0), sender, new String[0]);
        assertFalse(hasMessage("Hidden action"));
        assertTrue(registry.complete(installed.get(0), sender, new String[] {""}).isEmpty());
        try {
            registry.execute(hidden, sender, new String[0]);
            fail("Accepted a denied command");
        } catch (CommandException failure) {
            assertEquals("commands.generic.permission", failure.getMessage());
        }
    }

    @Test
    public void standaloneAndRootCommandsStopTogetherAndRestartWithoutRegistration()
            throws Exception {
        List<String[]> calls = new ArrayList<>();
        CommandNode action = CommandNode.command("action", "Run an action")
                .executes((source, args) -> calls.add(args)).build();
        registry.register(action, true);
        registry.start();
        registry.start();
        assertEquals(2, installed.size());
        registry.stop();
        registry.execute(installed.get(0), sender, new String[] {"action"});
        registry.execute(action, sender, new String[0]);
        assertTrue(calls.isEmpty());
        assertTrue(registry.complete(installed.get(0), sender, new String[] {""}).isEmpty());
        assertTrue(hasMessage("Commands are unavailable"));
        registry.start();
        registry.execute(action, sender, new String[0]);
        assertEquals(1, calls.size());
        assertEquals(2, installed.size());
    }

    @Test
    public void partialStartupDoesNotInstallSuccessfulEntryPointsAgain() {
        List<CommandNode> attempts = new ArrayList<>();
        CommandRegistry partial = new CommandRegistry(ClientThread.current(), (owner, command) -> {
            attempts.add(command);
            if (attempts.size() == 2)
                throw new IllegalStateException("Registration failed");
        }, new Languages(ClientThread.current()));
        CommandNode action = CommandNode.command("action", "Run an action").build();
        partial.register(action, true);
        try {
            partial.start();
            fail("Startup should fail");
        } catch (IllegalStateException failure) {
            assertEquals("Registration failed", failure.getMessage());
        }
        partial.stop();
        partial.start();
        assertEquals(3, attempts.size());
        assertEquals("12pit", attempts.get(0).name());
        assertEquals(action, attempts.get(1));
        assertEquals(action, attempts.get(2));
    }

    @Test
    public void conflictingAliasesAreRejectedWithoutRegisteringPartOfACommand() {
        registry.register(CommandNode.command("first", "First action").aliases("taken").build());
        try {
            registry.register(
                    CommandNode.command("second", "Second action").aliases("free", "taken").build(),
                    true);
            fail("Accepted conflicting aliases");
        } catch (IllegalArgumentException failure) {
            assertEquals("Duplicate command name: taken", failure.getMessage());
        }
        registry.register(CommandNode.command("second", "Second action").aliases("free").build());
        registry.start();
        assertEquals(1, installed.size());
        assertEquals(Arrays.asList("first", "taken", "second", "free"),
                registry.complete(installed.get(0), sender, new String[] {""}));
    }

    private boolean hasMessage(String text) {
        return messages.stream().anyMatch(message -> message.contains(text));
    }
}
