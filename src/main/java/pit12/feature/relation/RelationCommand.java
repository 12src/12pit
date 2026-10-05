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

import static pit12.runtime.languages.Languages.source;
import static pit12.shared.chat.ChatFeedback.reply;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.ICommandSender;
import pit12.feature.relation.api.IdentityLookupState;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationEntry;
import pit12.runtime.command.CommandNode;
import pit12.runtime.languages.Languages;
import pit12.runtime.player.TabPresence;
import pit12.shared.chat.ChatFeedback.Tone;
import pit12.shared.result.OperationResult;

final class RelationCommand {
    private final RelationFeature feature;
    private final TabPresence presence;
    private final Relation relation;
    private final Languages language;

    RelationCommand(RelationFeature feature, TabPresence presence, Relation relation,
            Languages language) {
        this.language = language;
        this.feature = feature;
        this.presence = presence;
        this.relation = relation;
    }

    CommandNode definition() {
        String name = relation.name().toLowerCase(Locale.ROOT);
        return CommandNode
                .command(name,
                        relation == Relation.FRIEND ? source("Manage friend relations")
                                : source("Manage enemy relations"))
                .arguments("<player>", 1, 1)
                .executes((sender, args) -> change(sender, "toggle", args[0]))
                .suggests((sender, args) -> playerNames(true))
                .child(CommandNode.command("list",
                        relation == Relation.FRIEND ? source("Show friend relations")
                                : source("Show enemy relations"))
                        .executes(this::list))
                .child(CommandNode.command("add", source("Add a player"))
                        .arguments("<player>", 1, 1)
                        .executes((sender, args) -> change(sender, "add", args[0]))
                        .suggests((sender, args) -> new ArrayList<>(presence.players().values())))
                .child(CommandNode.command("remove", source("Remove a player"))
                        .arguments("<player>", 1, 1)
                        .executes((sender, args) -> change(sender, "remove", args[0]))
                        .suggests((sender, args) -> playerNames(false)))
                .build();
    }

    private void list(ICommandSender sender, String[] args) {
        if (!ready(sender))
            return;
        List<RelationEntry> entries = feature.entries(relation);
        reply(sender, Tone.INFO,
                language.format("{0} ({1}):",
                        relation == Relation.FRIEND ? language.translate("Friends")
                                : language.translate("Enemies"),
                        entries.size()));
        for (RelationEntry entry : entries) {
            reply(sender, Tone.INFO,
                    "  " + entry.name()
                            + (entry.playerId() == null
                                    ? language.format(" (UUID {0})",
                                            lookupStatus(feature.resolutionOf(entry.name())))
                                    : ""));
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

    private String lookupStatus(IdentityLookupState state) {
        switch (state) {
            case WAITING:
                return language.translate("waiting");
            case RESOLVING:
                return language.translate("resolving");
            case RETRYING:
                return language.translate("retrying");
            case NOT_FOUND:
                return language.translate("not_found");
            case FAILED:
                return language.translate("failed");
            case CONFIRMED:
                return language.translate("confirmed");
            default:
                return language.translate("unknown");
        }
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
