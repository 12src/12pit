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

import static pit12.shared.chat.ChatFeedback.reply;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.ICommandSender;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationEntry;
import pit12.runtime.command.CommandNode;
import pit12.runtime.player.TabPresence;
import pit12.shared.chat.ChatFeedback.Tone;
import pit12.shared.result.OperationResult;

final class RelationCommand {
    private final RelationFeature feature;
    private final TabPresence presence;
    private final Relation relation;

    RelationCommand(RelationFeature feature, TabPresence presence, Relation relation) {
        this.feature = feature;
        this.presence = presence;
        this.relation = relation;
    }

    CommandNode definition() {
        String name = relation.name().toLowerCase(Locale.ROOT);
        return CommandNode.command(name, "Manage " + name + " relations")
                .arguments("<player>", 1, 1)
                .executes((sender, args) -> change(sender, "toggle", args[0]))
                .suggests((sender, args) -> playerNames(true))
                .child(CommandNode.command("list", "Show " + name + " relations")
                        .executes(this::list))
                .child(CommandNode.command("add", "Add a player").arguments("<player>", 1, 1)
                        .executes((sender, args) -> change(sender, "add", args[0]))
                        .suggests((sender, args) -> new ArrayList<>(presence.players().values())))
                .child(CommandNode.command("remove", "Remove a player").arguments("<player>", 1, 1)
                        .executes((sender, args) -> change(sender, "remove", args[0]))
                        .suggests((sender, args) -> playerNames(false)))
                .build();
    }

    private void list(ICommandSender sender, String[] args) {
        if (!ready(sender))
            return;
        List<RelationEntry> entries = feature.entries(relation);
        reply(sender, Tone.INFO, relation.name() + " (" + entries.size() + "):");
        for (RelationEntry entry : entries) {
            reply(sender, Tone.INFO,
                    "  " + entry.name() + (entry.playerId() == null ? " (UUID "
                            + feature.resolutionOf(entry.name()).name().toLowerCase(Locale.ROOT)
                            + ")" : ""));
        }
    }

    private void change(ICommandSender sender, String operation, String player) {
        if (ready(sender)) {
            OperationResult<Void> result = feature.change(relation, operation, player);
            reply(sender, result.succeeded() ? Tone.SUCCESS : Tone.ERROR, result.message());
        }
    }

    private boolean ready(ICommandSender sender) {
        String problem = feature.readinessProblem();
        if (problem == null)
            return true;
        reply(sender, Tone.WARNING, problem);
        return false;
    }

    private List<String> playerNames(boolean includeTab) {
        List<String> names = new ArrayList<>();
        if (includeTab)
            names.addAll(presence.players().values());
        for (RelationEntry entry : feature.entries(relation))
            names.add(entry.name());
        return names;
    }
}
