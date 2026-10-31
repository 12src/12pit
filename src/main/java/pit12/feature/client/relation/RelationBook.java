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
package pit12.feature.client.relation;

import static net.minecraft.util.EnumChatFormatting.WHITE;
import static pit12.runtime.languages.Languages.source;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationEntry;
import pit12.runtime.languages.Languages;
import pit12.shared.chat.ChatFeedback.Tone;

final class RelationBook {
    static final class Change {
        final boolean changed;
        final boolean succeeded;
        private final String message;
        private final Object[] parameters;

        Change(boolean changed, String message, Object... parameters) {
            this(changed, true, message, parameters);
        }

        private Change(boolean changed, boolean succeeded, String message, Object... parameters) {
            this.changed = changed;
            this.message = message;
            this.succeeded = succeeded;
            this.parameters = parameters;
        }

        static Change error(String message, Object... parameters) {
            return new Change(false, false, message, parameters);
        }

        String message(Languages language, boolean chat) {
            if (!chat || parameters.length == 0)
                return language.format(message, parameters);
            Tone tone = succeeded ? Tone.SUCCESS : Tone.ERROR;
            Object[] formatted = new Object[parameters.length];
            for (int index = 0; index < parameters.length; index++)
                formatted[index] = tone.argument(WHITE, parameters[index]);
            return language.format(message, formatted);
        }
    }

    private final Languages language;

    RelationBook(Languages language) {
        this.language = language;
    }

    private final Map<UUID, RelationEntry> byId = new LinkedHashMap<>();
    private final Map<String, RelationEntry> pending = new LinkedHashMap<>();

    void replace(Collection<RelationEntry> loaded) {
        byId.clear();
        pending.clear();
        for (RelationEntry entry : loaded) {
            if (entry.playerId() == null) {
                pending.put(key(entry.name()), entry);
            } else {
                byId.put(entry.playerId(), entry);
            }
        }
    }

    Relation relationOf(UUID id) {
        RelationEntry entry = byId.get(id);
        return entry == null ? Relation.NONE : entry.relation();
    }

    RelationEntry pending(String name) {
        return pending.get(key(name));
    }

    Change change(Relation target, String action, String name, Map<UUID, String> online) {
        if (!validName(name)) {
            return Change.error(source("Enter a valid player name"));
        }
        List<RelationEntry> matches = named(name);
        RelationEntry targetEntry = null;
        for (RelationEntry entry : matches) {
            if (entry.relation() == target) {
                if (targetEntry != null) {
                    return Change.error(source("Multiple players on this list have that name"));
                }
                targetEntry = entry;
            }
        }
        if ("remove".equals(action) || ("toggle".equals(action) && targetEntry != null)) {
            if (targetEntry == null) {
                return Change.error(source("{0} is not on the {1} list"), name, listName(target));
            }
            remove(targetEntry);
            return new Change(true, source("{0} removed from the {1} list"), targetEntry.name(),
                    listName(target));
        }
        if (matches.size() > 1) {
            return Change.error(source("Multiple saved players have that name"));
        }
        RelationEntry saved = matches.isEmpty() ? null : matches.get(0);
        UUID tabId = null;
        String tabName = null;
        for (Map.Entry<UUID, String> player : online.entrySet()) {
            if (player.getValue().equalsIgnoreCase(name)) {
                if (tabId != null && !tabId.equals(player.getKey())) {
                    return Change.error(source("Multiple Tab players have that name"));
                }
                tabId = player.getKey();
                tabName = player.getValue();
            }
        }
        if (tabId != null) {
            if (saved != null && saved.playerId() != null && !saved.playerId().equals(tabId)) {
                return Change.error(source("That name belongs to a different saved UUID"));
            }
            if (saved != null && saved.playerId() == null) {
                pending.remove(key(saved.name()));
            }
            RelationEntry old = byId.put(tabId, new RelationEntry(tabId, tabName, target));
            Relation previous = old == null ? Relation.NONE : old.relation();
            boolean changed = (saved != null && saved.playerId() == null) || old == null
                    || previous != target || !old.name().equals(tabName);
            return new Change(changed, source("{0} added to the {1} list"), tabName,
                    listName(target));
        }
        if (saved != null && saved.playerId() != null) {
            if (saved.relation() == target) {
                return new Change(false, source("{0} is already on the {1} list"), saved.name(),
                        listName(target));
            }
            byId.put(saved.playerId(), new RelationEntry(saved.playerId(), saved.name(), target));
            return new Change(true, source("{0} added to the {1} list"), saved.name(),
                    listName(target));
        }
        if (saved != null && saved.relation() == target) {
            return new Change(false, source("{0} is already on the {1} list (UUID pending)"),
                    saved.name(), listName(target));
        }
        RelationEntry waiting =
                new RelationEntry(null, saved == null ? name : saved.name(), target);
        pending.put(key(name), waiting);
        return new Change(true, source("{0} added to the {1} list (UUID pending)"), waiting.name(),
                listName(target));
    }

    Change remove(Relation target, UUID id) {
        RelationEntry entry = byId.get(id);
        if (entry == null || entry.relation() != target) {
            return Change.error(source("Player is not on the {0} list"), listName(target));
        }
        byId.remove(id);
        return new Change(true, source("{0} removed from the {1} list"), entry.name(),
                listName(target));
    }

    private String listName(Relation relation) {
        return relation == Relation.FRIEND ? language.translate("friend")
                : language.translate("enemy");
    }

    boolean observe(UUID id, String name) {
        if (!validName(name)) {
            return false;
        }
        RelationEntry known = byId.get(id);
        if (known != null) {
            if (known.name().equals(name)) {
                return false;
            }
            byId.put(id, new RelationEntry(id, name, known.relation()));
            return true;
        }
        RelationEntry waiting = pending.remove(key(name));
        if (waiting == null) {
            return false;
        }
        byId.put(id, new RelationEntry(id, name, waiting.relation()));
        return true;
    }

    boolean bind(RelationEntry expected, UUID id, String name) {
        if (pending(expected.name()) != expected) {
            return false;
        }
        pending.remove(key(expected.name()));
        byId.put(id, new RelationEntry(id, validName(name) ? name : expected.name(),
                expected.relation()));
        return true;
    }

    List<RelationEntry> entries() {
        List<RelationEntry> all = new ArrayList<>(byId.values());
        all.addAll(pending.values());
        return all;
    }

    List<RelationEntry> entries(Relation relation) {
        List<RelationEntry> result = new ArrayList<>();
        for (RelationEntry entry : entries()) {
            if (entry.relation() == relation) {
                result.add(entry);
            }
        }
        return result;
    }

    private List<RelationEntry> named(String name) {
        List<RelationEntry> result = new ArrayList<>();
        for (RelationEntry entry : entries()) {
            if (entry.name().equalsIgnoreCase(name)) {
                result.add(entry);
            }
        }
        return result;
    }

    private void remove(RelationEntry entry) {
        if (entry.playerId() == null) {
            pending.remove(key(entry.name()));
        } else {
            byId.remove(entry.playerId());
        }
    }

    private static boolean validName(String name) {
        return name != null && !name.isEmpty() && name.length() <= 48;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
