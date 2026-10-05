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
package pit12.feature.eventlist;

import static pit12.runtime.languages.Languages.source;
import static pit12.shared.chat.ChatFeedback.reply;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.ICommandSender;
import pit12.runtime.command.CommandNode;
import pit12.runtime.languages.Languages;
import pit12.shared.chat.ChatFeedback.Tone;

final class EventListCommand {
    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault());
    private final EventListFeature feature;
    private final Languages language;

    EventListCommand(EventListFeature feature, Languages language) {
        this.language = language;
        this.feature = feature;
    }

    CommandNode definition() {
        return CommandNode.command("event", source("Show stored events")).aliases("events")
                .arguments("<event>", 1, 2).executes(this::list).suggests((sender, args) -> {
                    List<String> names = new ArrayList<>();
                    if (args.length == 1) {
                        for (EventType type : EventType.values()) {
                            names.add(type.id);
                        }
                    }
                    return names;
                }).build();
    }

    private void list(ICommandSender sender, String[] args) {
        String name = String.join(" ", args);
        EventType type = EventType.fromName(name);
        if (type == null) {
            reply(sender, Tone.ERROR, language.format("Unknown event: {0}", name));
            return;
        }
        List<PitEvent> matching = feature.events(type);
        reply(sender, Tone.INFO, language.format("{0} events ({1}):",
                language.translate(type.displayName), matching.size()));
        long now = System.currentTimeMillis();
        for (PitEvent event : matching) {
            reply(sender, Tone.INFO,
                    "  " + language.translate(event.type.displayName) + " - "
                            + TIME.format(Instant.ofEpochMilli(event.timestamp)) + " - "
                            + countdown(event, now));
        }
        if (matching.isEmpty()) {
            reply(sender, Tone.INFO, language.translate("  No stored events"));
        }
    }

    private String countdown(PitEvent event, long now) {
        PitEvent.Phase phase = event.phase(now);
        long deadline = phase == PitEvent.Phase.FUTURE ? event.timestamp
                : phase == PitEvent.Phase.PREPARING ? event.startsAt : event.endsAt;
        long seconds = Math.max(0L, deadline - now + 999L) / 1000L;
        long hours = seconds / 3600L;
        long minutes = seconds / 60L % 60L;
        String tail = number(seconds % 60L) + "s";
        if (hours > 0L) {
            return number(hours) + "h " + number(minutes) + "m " + tail;
        }
        return minutes > 0L ? number(minutes) + "m " + tail : tail;
    }

    private String number(long value) {
        return value < 10L ? "0" + value : Long.toString(value);
    }
}
