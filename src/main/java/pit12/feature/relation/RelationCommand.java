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
package pit12.feature.relation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationEntry;
import pit12.runtime.player.TabPresence;

final class RelationCommand extends CommandBase {
    private final RelationFeature feature;
    private final TabPresence presence;
    private final Relation relation;

    RelationCommand(RelationFeature feature, TabPresence presence, Relation relation) {
        this.feature = feature;
        this.presence = presence;
        this.relation = relation;
    }

    @Override
    public String getCommandName() {
        return relation.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/12pit " + getCommandName() + " [list|add|remove|player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String problem = feature.readinessProblem();
        if (problem != null) {
            reply(sender, problem);
            return;
        }
        if (args.length == 0 || args.length == 1 && "list".equalsIgnoreCase(args[0])) {
            List<RelationEntry> entries = feature.entries(relation);
            reply(sender, relation.name() + " (" + entries.size() + "):");
            for (RelationEntry entry : entries) {
                reply(sender,
                        "  " + entry.name() + (entry.playerId() == null ? " (UUID "
                                + feature.resolutionOf(entry.name()).name().toLowerCase(Locale.ROOT)
                                + ")" : ""));
            }
            return;
        }
        if (args.length == 1 && !"add".equalsIgnoreCase(args[0])
                && !"remove".equalsIgnoreCase(args[0])) {
            reply(sender, feature.change(relation, "toggle", args[0]).message());
            return;
        }
        if (args.length == 2
                && ("add".equalsIgnoreCase(args[0]) || "remove".equalsIgnoreCase(args[0]))) {
            reply(sender,
                    feature.change(relation, args[0].toLowerCase(Locale.ROOT), args[1]).message());
            return;
        }
        reply(sender, getCommandUsage(sender));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args,
            BlockPos position) {
        if (args.length == 1) {
            List<String> options = new ArrayList<String>(Arrays.asList("list", "add", "remove"));
            options.addAll(presence.players().values());
            for (RelationEntry entry : feature.entries(relation)) {
                options.add(entry.name());
            }
            return getListOfStringsMatchingLastWord(args, options);
        }
        if (args.length == 2
                && ("add".equalsIgnoreCase(args[0]) || "remove".equalsIgnoreCase(args[0]))) {
            List<String> options = new ArrayList<String>();
            if ("remove".equalsIgnoreCase(args[0])) {
                for (RelationEntry entry : feature.entries(relation)) {
                    options.add(entry.name());
                }
            } else {
                options.addAll(presence.players().values());
            }
            return getListOfStringsMatchingLastWord(args, options);
        }
        return null;
    }

    private static void reply(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(
                EnumChatFormatting.AQUA + "[12pit]" + EnumChatFormatting.RESET + " " + message));
    }
}
