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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandNotFoundException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.junit.After;
import org.junit.Test;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.languages.Languages;
import pit12.shared.concurrent.ClientThread;

public final class CommandRegistryTest {
    private final ClientThread client = ClientThread.current();
    private final ConfigCatalog configs = new ConfigCatalog(client);
    private final Map<String, CommandNode> installed = new HashMap<>();
    private final CommandRegistry commands =
            new CommandRegistry(client, configs, (registry, command) -> {
                installed.put(command.name(), command);
                return () -> installed.remove(command.name());
            }, new Languages(client));
    private final Sender sender = new Sender();

    @After
    public void stopCommands() {
        commands.stop();
    }

    @Test
    public void nestedCommandsAndAliasesPassOnlyTheirArgumentsToHandlers() throws CommandException {
        List<List<String>> calls = new ArrayList<>();
        CommandNode action =
                CommandNode.command("set", "").aliases("go").arguments("<name> <value>", 2, 2)
                        .executes((source, args) -> calls.add(Arrays.asList(args))).build();
        commands.register(CommandNode.command("tools", "").aliases("t").child(action).build());
        commands.start();
        commands.execute(installed.get("12pit"), sender,
                new String[] {"tools", "set", "Alex", "7"});
        commands.execute(installed.get("12pit"), sender, new String[] {"T", "GO", "Sam", "9"});
        assertEquals(Arrays.asList(Arrays.asList("Alex", "7"), Arrays.asList("Sam", "9")), calls);
    }

    @Test
    public void featureStateControlsCommandRegistrationExecutionAndCompletion()
            throws CommandException {
        FeatureConfig owner = new FeatureConfig("sample", "Sample", null, "") {};
        configs.register(owner);
        List<String> calls = new ArrayList<>();
        CommandNode command = CommandNode.command("tools", "")
                .executes((source, args) -> calls.add("called")).build();
        commands.register(command, owner, true);
        commands.start();
        CommandNode root = installed.get("12pit");
        assertFalse(installed.containsKey("tools"));
        assertFalse(commands.available(command));
        assertTrue(commands.complete(root, sender, new String[] {""}).isEmpty());
        try {
            commands.execute(root, sender, new String[] {"tools"});
            fail("An inactive feature command should be unavailable");
        } catch (CommandNotFoundException expected) {
            assertTrue(calls.isEmpty());
        }
        commands.setActive(owner, true);
        assertTrue(installed.containsKey("tools"));
        assertTrue(commands.available(command));
        assertEquals(Collections.singletonList("tools"),
                commands.complete(root, sender, new String[] {""}));
        commands.execute(root, sender, new String[] {"tools"});
        assertEquals(Collections.singletonList("called"), calls);
        owner.setEnabled(false);
        assertFalse(installed.containsKey("tools"));
        assertFalse(commands.available(command));
        assertTrue(commands.complete(root, sender, new String[] {""}).isEmpty());
        owner.setEnabled(true);
        assertTrue(installed.containsKey("tools"));
        assertEquals(Collections.singletonList("tools"),
                commands.complete(root, sender, new String[] {""}));
        commands.setActive(owner, false);
        assertFalse(installed.containsKey("tools"));
        assertTrue(commands.complete(root, sender, new String[] {""}).isEmpty());
        commands.stop();
        assertTrue(installed.isEmpty());
    }

    @Test
    public void permissionsApplyToExecutionAndCompletion() throws CommandException {
        List<String> calls = new ArrayList<>();
        commands.register(CommandNode.command("restricted", "")
                .requires(source -> source.canCommandSenderUseCommand(2, "restricted"))
                .executes((source, args) -> calls.add("called")).build());
        commands.start();
        CommandNode root = installed.get("12pit");
        sender.allowed = false;
        assertTrue(commands.complete(root, sender, new String[] {""}).isEmpty());
        try {
            commands.execute(root, sender, new String[] {"restricted"});
            fail("A restricted command should require permission");
        } catch (CommandException expected) {
            assertTrue(calls.isEmpty());
        }
        sender.allowed = true;
        assertEquals(Collections.singletonList("restricted"),
                commands.complete(root, sender, new String[] {""}));
        commands.execute(root, sender, new String[] {"restricted"});
        assertEquals(Collections.singletonList("called"), calls);
    }

    @Test
    public void completionUsesTheCommandPathAndFiltersCandidates() {
        List<List<String>> requests = new ArrayList<>();
        CommandNode action = CommandNode.command("set", "").aliases("go").arguments("<name>", 1, 1)
                .suggests((source, args) -> {
                    requests.add(Arrays.asList(args));
                    return Arrays.asList("Alice", "alex", "Bob", "Alice");
                }).build();
        commands.register(CommandNode.command("tools", "").aliases("t").child(action).build());
        commands.start();
        List<String> result =
                commands.complete(installed.get("12pit"), sender, new String[] {"t", "go", "AL"});
        assertEquals(Collections.singletonList(Collections.singletonList("AL")), requests);
        assertEquals(2, result.size());
        assertEquals(new HashSet<>(Arrays.asList("Alice", "alex")), new HashSet<>(result));
    }

    @Test
    public void invalidArgumentCountsProduceFeedbackAndKeepTheHandlerIdle()
            throws CommandException {
        List<List<String>> calls = new ArrayList<>();
        CommandNode command = CommandNode.command("set", "").arguments("<name> [value]", 1, 2)
                .executes((source, args) -> calls.add(Arrays.asList(args))).build();
        commands.register(command);
        commands.start();
        commands.execute(command, sender, new String[0]);
        commands.execute(command, sender, new String[] {"Alex", "7", "extra"});
        assertTrue(calls.isEmpty());
        assertFalse(sender.messages.isEmpty());
        commands.execute(command, sender, new String[] {"Alex", "7"});
        assertEquals(Collections.singletonList(Arrays.asList("Alex", "7")), calls);
    }

    private static final class Sender implements ICommandSender {
        final List<String> messages = new ArrayList<>();
        boolean allowed = true;

        @Override
        public String getName() {
            return "Tester";
        }

        @Override
        public IChatComponent getDisplayName() {
            return new ChatComponentText(getName());
        }

        @Override
        public void addChatMessage(IChatComponent component) {
            messages.add(component.getUnformattedText());
        }

        @Override
        public boolean canCommandSenderUseCommand(int level, String command) {
            return allowed;
        }

        @Override
        public BlockPos getPosition() {
            return new BlockPos(0, 0, 0);
        }

        @Override
        public Vec3 getPositionVector() {
            return new Vec3(0, 0, 0);
        }

        @Override
        public World getEntityWorld() {
            return null;
        }

        @Override
        public Entity getCommandSenderEntity() {
            return null;
        }

        @Override
        public boolean sendCommandFeedback() {
            return true;
        }

        @Override
        public void setCommandStat(CommandResultStats.Type type, int amount) {}
    }
}
