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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import pit12.feature.Feature;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationEntry;
import pit12.feature.relation.api.RelationListener;
import pit12.feature.relation.api.Relations;
import pit12.feature.relation.storage.RelationIoWorker;
import pit12.feature.relation.storage.RelationStore;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceListener;

public final class RelationFeature implements Feature, Relations, TabPresenceListener {
    private static final Logger LOGGER = Logger.getLogger(RelationFeature.class.getName());
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final TabPresence presence;
    private final Path path;
    private final RelationBook book = new RelationBook();
    private final List<RelationListener> listeners = new ArrayList<RelationListener>();
    private final List<Runnable> changeListeners = new ArrayList<Runnable>();
    private RelationIoWorker worker;
    private ExecutorService lookupWorker;
    private boolean started;
    private boolean registered;
    private boolean ready;
    private boolean failed;
    private boolean dirty;
    private long generation;

    public RelationFeature(TabPresence presence, Path path) {
        this.presence = presence;
        this.path = path;
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        if (worker != null && worker.isAlive()) {
            throw new IllegalStateException("Previous relation worker is still stopping");
        }
        ready = false;
        failed = false;
        dirty = false;
        started = true;
        long activeGeneration = ++generation;
        worker = new RelationIoWorker(new RelationStore(path), new IoListener(activeGeneration));
        lookupWorker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "12pit-relation-lookup");
            thread.setDaemon(true);
            return thread;
        });
        presence.addListener(this);
        if (!registered) {
            // Forge's client command registry has no matching unregister operation.
            ClientCommandHandler.instance.registerCommand(new RelationCommand(this, presence));
            registered = true;
        }
        worker.start();
    }

    @Override
    public void stop() {
        if (!started && worker == null) {
            return;
        }
        started = false;
        generation++;
        presence.removeListener(this);
        ExecutorService closingLookups = lookupWorker;
        if (closingLookups != null) {
            closingLookups.shutdownNow();
            lookupWorker = null;
        }
        RelationIoWorker closing = worker;
        if (closing != null) {
            closing.closeAfter(ready && dirty ? book.entries() : null);
            try {
                closing.awaitClose(2000L);
                if (closing.isAlive()) {
                    closing.interrupt();
                    closing.awaitClose(2000L);
                }
            } catch (InterruptedException interrupted) {
                closing.interrupt();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping relation worker",
                        interrupted);
            }
            if (closing.isAlive()) {
                throw new IllegalStateException("Relation worker did not stop");
            }
            worker = null;
        }
        if (closingLookups != null) {
            try {
                if (!closingLookups.awaitTermination(7000L, TimeUnit.MILLISECONDS)) {
                    LOGGER.warning("Relation lookup worker did not stop after cancellation");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping relation lookups",
                        interrupted);
            }
        }
        listeners.clear();
        changeListeners.clear();
        ready = false;
    }

    @Override
    public Relation relationOf(UUID playerId) {
        return ready ? book.relationOf(playerId) : Relation.NONE;
    }

    @Override
    public List<RelationEntry> entries(Relation relation) {
        return ready ? Collections.unmodifiableList(book.entries(relation))
                : Collections.<RelationEntry>emptyList();
    }

    @Override
    public List<RelationEntry> presentRelations() {
        if (!ready) {
            return Collections.emptyList();
        }
        List<RelationEntry> result = new ArrayList<RelationEntry>();
        for (RelationEntry entry : book.entries()) {
            if (entry.playerId() != null && presence.contains(entry.playerId())) {
                result.add(entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public void addListener(RelationListener listener) {
        if (!listeners.contains(Objects.requireNonNull(listener, "listener"))) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(RelationListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void addChangeListener(Runnable listener) {
        if (!changeListeners.contains(Objects.requireNonNull(listener, "listener"))) {
            changeListeners.add(listener);
        }
    }

    @Override
    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void notifyChanged() {
        for (Runnable listener : new ArrayList<Runnable>(changeListeners)) {
            try {
                listener.run();
            } catch (RuntimeException failure) {
                LOGGER.log(Level.WARNING, "Relation listener failed", failure);
            }
        }
    }

    @Override
    public void onPlayerSeen(UUID playerId, String name, boolean joined) {
        if (!ready) {
            return;
        }
        RelationBook.Observation observed = book.observe(playerId, name);
        if (observed != null && observed.changed) {
            save();
        }
        if (observed != null && observed.bound) {
            notifyRelation(playerId, Relation.NONE, observed.relation);
        }
        if (observed != null && (joined || observed.bound)) {
            notifyPresence(playerId, observed.relation, true);
        }
    }

    @Override
    public void onPlayerLeft(UUID playerId) {
        Relation relation = book.relationOf(playerId);
        if (ready && relation != Relation.NONE) {
            notifyPresence(playerId, relation, false);
        }
    }

    @Override
    public String readinessProblem() {
        return !started ? "Relations are unavailable"
                : failed ? "Relations could not be loaded; the file is read-only"
                        : !ready ? "Relations are still loading" : null;
    }

    public String change(Relation target, String action, String name) {
        String problem = readinessProblem();
        if (problem != null) {
            return problem;
        }
        RelationBook.Change result = book.change(target, action, name, presence.players());
        apply(result, true);
        return result.message;
    }

    @Override
    public List<String> changeMany(Relation target, String action, List<RelationEntry> entries) {
        String problem = readinessProblem();
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        if (target == Relation.NONE || !"add".equals(action) && !"remove".equals(action)) {
            throw new IllegalArgumentException("Invalid relation change");
        }
        for (RelationEntry entry : entries) {
            if (entry.relation() != target || "add".equals(action) && entry.playerId() != null) {
                throw new IllegalArgumentException("Invalid relation entry");
            }
        }
        List<String> messages = new ArrayList<String>(entries.size());
        boolean changed = false;
        for (RelationEntry entry : entries) {
            RelationBook.Change result = entry.playerId() == null
                    ? book.change(target, action, entry.name(), presence.players())
                    : book.remove(target, entry.playerId());
            changed |= result.changed;
            apply(result, false);
            messages.add(result.message);
        }
        if (changed) {
            save();
        }
        return messages;
    }

    @Override
    public void replaceAll(List<RelationEntry> entries) {
        String problem = readinessProblem();
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        Set<UUID> ids = new HashSet<UUID>();
        Set<String> pending = new HashSet<String>();
        for (RelationEntry entry : entries) {
            if (entry.relation() == Relation.NONE
                    || entry.playerId() == null
                            && !pending.add(entry.name().toLowerCase(java.util.Locale.ROOT))
                    || entry.playerId() != null && !ids.add(entry.playerId())) {
                throw new IllegalArgumentException("Duplicate or invalid relation identity");
            }
        }
        Map<UUID, Relation> previous = new HashMap<UUID, Relation>();
        for (RelationEntry entry : book.entries()) {
            if (entry.playerId() != null) {
                previous.put(entry.playerId(), entry.relation());
            }
        }
        book.replace(entries);
        for (Map.Entry<UUID, String> player : presence.players().entrySet()) {
            book.observe(player.getKey(), player.getValue());
        }
        Map<UUID, Relation> current = new HashMap<UUID, Relation>();
        for (RelationEntry entry : book.entries()) {
            if (entry.playerId() != null) {
                current.put(entry.playerId(), entry.relation());
            } else {
                lookup(entry);
            }
        }
        ids.addAll(previous.keySet());
        ids.addAll(current.keySet());
        for (UUID id : ids) {
            Relation before = previous.containsKey(id) ? previous.get(id) : Relation.NONE;
            Relation after = current.containsKey(id) ? current.get(id) : Relation.NONE;
            if (before == after) {
                continue;
            }
            notifyRelation(id, before, after);
            if (presence.contains(id)) {
                if (before != Relation.NONE) {
                    notifyPresence(id, before, false);
                }
                if (after != Relation.NONE) {
                    notifyPresence(id, after, true);
                }
            }
        }
        save();
    }

    private void apply(RelationBook.Change result, boolean persist) {
        if (persist && result.changed) {
            save();
        }
        if (result.lookup != null) {
            lookup(result.lookup);
        }
        if (!result.changed || result.playerId == null) {
            return;
        }
        if (result.previous != result.current) {
            notifyRelation(result.playerId, result.previous, result.current);
        }
        if (result.previous != result.current && presence.contains(result.playerId)) {
            if (result.previous != Relation.NONE) {
                notifyPresence(result.playerId, result.previous, false);
            }
            if (result.current != Relation.NONE) {
                notifyPresence(result.playerId, result.current, true);
            }
        }
    }

    private void lookup(RelationEntry waiting) {
        long requestGeneration = generation;
        lookupWorker.execute(() -> {
            MojangProfileLookup.Profile profile;
            try {
                profile = new MojangProfileLookup().lookup(waiting.name());
            } catch (IOException failure) {
                LOGGER.log(Level.FINE, "Mojang lookup failed for " + waiting.name(), failure);
                return;
            }
            if (profile == null) {
                return;
            }
            dispatch(requestGeneration, () -> {
                String tabName = presence.players().get(profile.id);
                RelationBook.Change bound =
                        book.bind(waiting, profile.id, tabName == null ? profile.name : tabName);
                if (bound == null) {
                    return;
                }
                save();
                if (bound.previous != bound.current) {
                    notifyRelation(bound.playerId, bound.previous, bound.current);
                }
                if (presence.contains(bound.playerId)) {
                    if (bound.previous != Relation.NONE && bound.previous != bound.current) {
                        notifyPresence(bound.playerId, bound.previous, false);
                    }
                    if (bound.previous == Relation.NONE || bound.previous != bound.current) {
                        notifyPresence(bound.playerId, bound.current, true);
                    }
                }
            });
        });
    }

    private void notifyRelation(UUID id, Relation previous, Relation current) {
        for (RelationListener listener : new ArrayList<RelationListener>(listeners)) {
            listener.onRelationChanged(id, previous, current);
        }
    }

    private void save() {
        dirty = true;
        if (worker != null) {
            worker.requestWrite(book.entries());
        }
        notifyChanged();
    }

    private void notifyPresence(UUID id, Relation relation, boolean present) {
        for (RelationListener listener : new ArrayList<RelationListener>(listeners)) {
            listener.onPresenceChanged(id, relation, present);
        }
    }

    private void dispatch(long taskGeneration, Runnable task) {
        minecraft.addScheduledTask(() -> {
            if (started && generation == taskGeneration) {
                task.run();
            }
        });
    }

    private final class IoListener implements RelationIoWorker.Listener {
        private final long taskGeneration;

        private IoListener(long taskGeneration) {
            this.taskGeneration = taskGeneration;
        }

        @Override
        public void loaded(List<RelationEntry> entries) {
            dispatch(taskGeneration, () -> {
                book.replace(entries);
                ready = true;
                for (Map.Entry<UUID, String> player : presence.players().entrySet()) {
                    RelationBook.Observation observed =
                            book.observe(player.getKey(), player.getValue());
                    if (observed != null && observed.changed) {
                        save();
                    }
                }
                for (RelationEntry entry : book.entries()) {
                    if (entry.playerId() != null && presence.contains(entry.playerId())) {
                        notifyPresence(entry.playerId(), entry.relation(), true);
                    }
                }
                for (RelationListener listener : new ArrayList<RelationListener>(listeners)) {
                    listener.onRelationsLoaded();
                }
                notifyChanged();
            });
        }

        @Override
        public void loadFailed(Exception failure) {
            LOGGER.log(Level.WARNING, "Failed to load relations from " + path, failure);
            dispatch(taskGeneration, () -> {
                failed = true;
                notifyChanged();
            });
        }

        @Override
        public void writeFailed(IOException failure) {
            LOGGER.log(Level.WARNING, "Failed to save relations to " + path, failure);
            dispatch(taskGeneration, () -> {
                if (minecraft.thePlayer != null) {
                    minecraft.thePlayer.addChatMessage(
                            new ChatComponentText(EnumChatFormatting.AQUA + "[12pit]"
                                    + EnumChatFormatting.RESET + " Relations could not be saved"));
                }
            });
        }
    }
}
