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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationEntry;

final class RelationBook {
    static final class Change {
        final UUID playerId;
        final Relation previous;
        final Relation current;
        final RelationEntry lookup;
        final boolean changed;
        final String message;

        Change(UUID playerId, Relation previous, Relation current, RelationEntry lookup,
                boolean changed, String message) {
            this.playerId = playerId;
            this.previous = previous;
            this.current = current;
            this.lookup = lookup;
            this.changed = changed;
            this.message = message;
        }

        static Change error(String message) {
            return new Change(null, Relation.NONE, Relation.NONE, null, false, message);
        }
    }
    static final class Observation {
        final Relation relation;
        final boolean bound;
        final boolean changed;

        Observation(Relation relation, boolean bound, boolean changed) {
            this.relation = relation;
            this.bound = bound;
            this.changed = changed;
        }
    }

    private final Map<UUID, RelationEntry> byId = new LinkedHashMap<UUID, RelationEntry>();
    private final Map<String, RelationEntry> pending = new LinkedHashMap<String, RelationEntry>();

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

    RelationEntry rebind(UUID expectedId, String expectedName, UUID playerId, String name) {
        RelationEntry current =
                expectedId == null ? pending.get(key(expectedName)) : byId.get(expectedId);
        if (current == null
                || expectedName != null && !current.name().equalsIgnoreCase(expectedName)) {
            return null;
        }
        if (current.playerId() == null) {
            pending.remove(key(current.name()));
        } else {
            byId.remove(current.playerId());
        }
        pending.remove(key(name));
        for (RelationEntry entry : new ArrayList<RelationEntry>(byId.values())) {
            if (entry.playerId().equals(playerId) || entry.name().equalsIgnoreCase(name)) {
                byId.remove(entry.playerId());
            }
        }
        RelationEntry replacement = new RelationEntry(playerId, name, current.relation());
        byId.put(playerId, replacement);
        return replacement;
    }

    void applyRemotePatch(String action, UUID playerId, String name, Relation relation) {
        String nameKey = key(name);
        if ("remove".equals(action)) {
            if (playerId != null && byId.remove(playerId) != null) {
                return;
            }
            pending.remove(nameKey);
            for (RelationEntry entry : new ArrayList<RelationEntry>(byId.values())) {
                if (entry.name().equalsIgnoreCase(name)) {
                    byId.remove(entry.playerId());
                }
            }
            return;
        }
        pending.remove(nameKey);
        for (RelationEntry entry : new ArrayList<RelationEntry>(byId.values())) {
            if (!entry.playerId().equals(playerId) && entry.name().equalsIgnoreCase(name)) {
                byId.remove(entry.playerId());
            }
        }
        if (playerId == null) {
            pending.put(nameKey, new RelationEntry(null, name, relation));
        } else {
            byId.put(playerId, new RelationEntry(playerId, name, relation));
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
        if (name == null || name.isEmpty() || name.length() > 48) {
            return Change.error("Enter a valid player name");
        }
        List<RelationEntry> matches = named(name);
        RelationEntry targetEntry = null;
        for (RelationEntry entry : matches) {
            if (entry.relation() == target) {
                if (targetEntry != null) {
                    return Change.error("Multiple players on this list have that name");
                }
                targetEntry = entry;
            }
        }
        if ("remove".equals(action) || ("toggle".equals(action) && targetEntry != null)) {
            if (targetEntry == null) {
                return Change.error(name + " is not on the "
                        + target.name().toLowerCase(Locale.ROOT) + " list");
            }
            remove(targetEntry);
            return new Change(targetEntry.playerId(), target, Relation.NONE, null, true,
                    targetEntry.name() + " removed from the "
                            + target.name().toLowerCase(Locale.ROOT) + " list");
        }
        if (matches.size() > 1) {
            return Change.error("Multiple saved players have that name");
        }
        RelationEntry saved = matches.isEmpty() ? null : matches.get(0);
        UUID tabId = null;
        String tabName = null;
        for (Map.Entry<UUID, String> player : online.entrySet()) {
            if (player.getValue().equalsIgnoreCase(name)) {
                if (tabId != null && !tabId.equals(player.getKey())) {
                    return Change.error("Multiple Tab players have that name");
                }
                tabId = player.getKey();
                tabName = player.getValue();
            }
        }
        if (tabId != null) {
            if (saved != null && saved.playerId() != null && !saved.playerId().equals(tabId)) {
                return Change.error("That name belongs to a different saved UUID");
            }
            if (saved != null && saved.playerId() == null) {
                pending.remove(key(saved.name()));
            }
            RelationEntry old = byId.put(tabId, new RelationEntry(tabId, tabName, target));
            Relation previous = old == null ? Relation.NONE : old.relation();
            boolean changed = (saved != null && saved.playerId() == null) || old == null
                    || previous != target || !old.name().equals(tabName);
            return new Change(tabId, previous, target, null, changed,
                    tabName + " added to the " + target.name().toLowerCase(Locale.ROOT) + " list");
        }
        if (saved != null && saved.playerId() != null) {
            if (saved.relation() == target) {
                return new Change(saved.playerId(), target, target, null, false, saved.name()
                        + " is already on the " + target.name().toLowerCase(Locale.ROOT) + " list");
            }
            byId.put(saved.playerId(), new RelationEntry(saved.playerId(), saved.name(), target));
            return new Change(saved.playerId(), saved.relation(), target, null, true, saved.name()
                    + " added to the " + target.name().toLowerCase(Locale.ROOT) + " list");
        }
        if (saved != null && saved.relation() == target) {
            return new Change(null, target, target, null, false,
                    saved.name() + " is already on the " + target.name().toLowerCase(Locale.ROOT)
                            + " list (UUID pending)");
        }
        RelationEntry waiting =
                new RelationEntry(null, saved == null ? name : saved.name(), target);
        pending.put(key(name), waiting);
        return new Change(null, saved == null ? Relation.NONE : saved.relation(), target, waiting,
                true, waiting.name() + " added to the " + target.name().toLowerCase(Locale.ROOT)
                        + " list (UUID pending)");
    }

    Change remove(Relation target, UUID id) {
        RelationEntry entry = byId.get(id);
        if (entry == null || entry.relation() != target) {
            return Change.error(
                    "Player is not on the " + target.name().toLowerCase(Locale.ROOT) + " list");
        }
        byId.remove(id);
        return new Change(id, target, Relation.NONE, null, true, entry.name() + " removed from the "
                + target.name().toLowerCase(Locale.ROOT) + " list");
    }

    Change removeByName(String name) {
        List<RelationEntry> matches = named(name);
        if (matches.isEmpty()) {
            return new Change(null, Relation.NONE, Relation.NONE, null, false, null);
        }
        if (matches.size() > 1) {
            return Change.error("Multiple saved players have that name");
        }
        RelationEntry entry = matches.get(0);
        remove(entry);
        return new Change(entry.playerId(), entry.relation(), Relation.NONE, null, true, null);
    }

    Observation observe(UUID id, String name) {
        RelationEntry known = byId.get(id);
        if (known != null) {
            if (validName(name) && !known.name().equals(name)) {
                byId.put(id, new RelationEntry(id, name, known.relation()));
                return new Observation(known.relation(), false, true);
            }
            return new Observation(known.relation(), false, false);
        }
        if (!validName(name)) {
            return null;
        }
        RelationEntry waiting = pending.remove(key(name));
        if (waiting == null) {
            return null;
        }
        byId.put(id, new RelationEntry(id, name, waiting.relation()));
        return new Observation(waiting.relation(), true, true);
    }

    Change bind(RelationEntry expected, UUID id, String name) {
        if (id == null || pending(expected.name()) != expected) {
            return null;
        }
        pending.remove(key(expected.name()));
        RelationEntry previous = byId.put(id, new RelationEntry(id,
                validName(name) ? name : expected.name(), expected.relation()));
        return new Change(id, previous == null ? Relation.NONE : previous.relation(),
                expected.relation(), null, true, null);
    }

    List<RelationEntry> entries() {
        List<RelationEntry> all = new ArrayList<RelationEntry>(byId.values());
        all.addAll(pending.values());
        return all;
    }

    List<RelationEntry> entries(Relation relation) {
        List<RelationEntry> result = new ArrayList<RelationEntry>();
        for (RelationEntry entry : entries()) {
            if (entry.relation() == relation) {
                result.add(entry);
            }
        }
        return result;
    }

    private List<RelationEntry> named(String name) {
        List<RelationEntry> result = new ArrayList<RelationEntry>();
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
