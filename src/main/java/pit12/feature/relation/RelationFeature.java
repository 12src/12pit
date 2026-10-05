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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.relation.api.IdentityLookupState;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationEntry;
import pit12.feature.relation.api.RelationListener;
import pit12.feature.relation.api.RelationReadiness;
import pit12.feature.relation.api.Relations;
import pit12.feature.relation.storage.RelationIoWorker;
import pit12.feature.relation.storage.RelationStorage;
import pit12.feature.relation.storage.RelationStore;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.languages.Languages;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceListener;
import pit12.shared.chat.ChatFeedback;
import pit12.shared.chat.ChatFeedback.Tone;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.result.OperationResult;
import pit12.shared.result.OperationResult.Status;

public final class RelationFeature implements ClientLifecycle, Relations, TabPresenceListener {
    private static final Logger LOGGER = Logger.getLogger(RelationFeature.class.getName());
    private final TabPresence presence;
    private final Path path;
    private final ClientThread client;
    private final RelationStorage storage;
    private final IdentityResolver resolver;
    private final LongSupplier time;
    private final boolean installAdapters;
    private final RelationBook book;
    private final Languages language;
    private final List<RelationListener> listeners = new ArrayList<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private RelationIoWorker worker;
    private ExecutorService lookupWorker;
    private PendingLookups lookups;
    private boolean started;
    private boolean ready;
    private String loadProblem;
    private boolean dirty;
    private long generation;
    private List<String> shownLookupProblems = Collections.emptyList();

    public RelationFeature(TabPresence presence, Path path, ClientThread client,
            CommandRegistry commands, Languages language) {
        this(presence, path, client, new RelationStore(path), new MojangProfileLookup()::lookup,
                () -> System.nanoTime() / 1000000L, true, language);
        commands.register(
                new RelationCommand(this, presence, Relation.FRIEND, language).definition());
        commands.register(
                new RelationCommand(this, presence, Relation.ENEMY, language).definition());
    }

    RelationFeature(TabPresence presence, Path path, ClientThread client, RelationStorage storage,
            IdentityResolver resolver, LongSupplier time, boolean installAdapters,
            Languages language) {
        this.language = language;
        book = new RelationBook(language);
        this.presence = presence;
        this.path = path;
        this.client = client;
        this.storage = storage;
        this.resolver = resolver;
        this.time = time;
        this.installAdapters = installAdapters;
    }

    @Override
    public void start() {
        client.check();
        if (started)
            return;
        if (worker != null && worker.isAlive())
            throw new IllegalStateException("Previous relation worker is still stopping");
        ready = false;
        loadProblem = null;
        dirty = false;
        shownLookupProblems = Collections.emptyList();
        book.replace(Collections.emptyList());
        started = true;
        long activeGeneration = ++generation;
        worker = new RelationIoWorker(storage, new IoListener(activeGeneration));
        lookupWorker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "12pit-relation-lookup");
            thread.setDaemon(true);
            return thread;
        });
        lookups = new PendingLookups(resolver, lookupWorker, client, time, this::bindResolved,
                this::notifyChanged);
        presence.addListener(this);
        if (installAdapters) {
            MinecraftForge.EVENT_BUS.register(this);
        }
        worker.start();
    }

    @Override
    public void stop() {
        client.check();
        if (!started && worker == null)
            return;
        started = false;
        generation++;
        presence.removeListener(this);
        if (installAdapters)
            MinecraftForge.EVENT_BUS.unregister(this);
        lookups.clear();
        ExecutorService closingLookups = lookupWorker;
        if (closingLookups != null)
            closingLookups.shutdownNow();
        lookupWorker = null;
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
            if (closing.isAlive())
                throw new IllegalStateException("Relation worker did not stop");
            worker = null;
        }
        if (closingLookups != null) {
            try {
                if (!closingLookups.awaitTermination(7000L, TimeUnit.MILLISECONDS))
                    LOGGER.warning("Relation lookup worker did not stop");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping relation lookups",
                        interrupted);
            }
        }
        ready = false;
        listeners.clear();
        changeListeners.clear();
    }

    @Override
    public RelationReadiness readiness() {
        client.check();
        return !started ? RelationReadiness.UNAVAILABLE
                : loadProblem != null ? RelationReadiness.FAILED
                        : ready ? RelationReadiness.READY : RelationReadiness.LOADING;
    }

    @Override
    public String readinessProblem() {
        switch (readiness()) {
            case UNAVAILABLE:
                return language.translate("Relations are unavailable");
            case FAILED:
                return language.format("Relations could not be loaded: {0}", loadProblem);
            case LOADING:
                return language.translate("Relations are still loading");
            default:
                return null;
        }
    }

    @Override
    public Relation relationOf(UUID playerId) {
        client.check();
        return ready ? book.relationOf(playerId) : Relation.NONE;
    }

    @Override
    public List<RelationEntry> entries(Relation relation) {
        client.check();
        return ready ? book.entries(relation) : Collections.emptyList();
    }

    @Override
    public IdentityLookupState resolutionOf(String name) {
        client.check();
        if (ready)
            for (RelationEntry entry : book.entries()) {
                if (entry.playerId() != null && entry.name().equalsIgnoreCase(name))
                    return IdentityLookupState.CONFIRMED;
            }
        return lookups == null ? IdentityLookupState.UNKNOWN : lookups.stateOf(name);
    }

    @Override
    public List<String> lookupProblems() {
        client.check();
        return lookups == null ? Collections.emptyList() : lookups.problems();
    }

    @Override
    public void addListener(RelationListener listener) {
        client.check();
        if (!listeners.contains(listener))
            listeners.add(listener);
    }

    @Override
    public void removeListener(RelationListener listener) {
        client.check();
        listeners.remove(listener);
    }

    @Override
    public void addChangeListener(Runnable listener) {
        client.check();
        if (!changeListeners.contains(listener))
            changeListeners.add(listener);
    }

    @Override
    public void removeChangeListener(Runnable listener) {
        client.check();
        changeListeners.remove(listener);
    }

    private void notifyChanged() {
        List<String> problems = lookupProblems();
        if (installAdapters && Minecraft.getMinecraft().thePlayer != null) {
            for (String problem : problems) {
                if (!shownLookupProblems.contains(problem)) {
                    ChatFeedback.reply(Minecraft.getMinecraft().thePlayer, Tone.WARNING, problem);
                }
            }
        }
        shownLookupProblems = problems;
        Listeners.notify(changeListeners, Runnable::run);
    }

    @Override
    public void onPlayerSeen(UUID playerId, String name, boolean joined) {
        client.check();
        if (!ready)
            return;
        List<RelationEntry> previous = book.entries();
        finish(previous, book.observe(playerId, name), false);
    }

    @Override
    public void onPlayerLeft(UUID playerId) {}

    public OperationResult<Void> change(Relation target, String action, String name) {
        client.check();
        String problem = readinessProblem();
        if (problem != null)
            return OperationResult.failure(Status.UNAVAILABLE, problem);
        List<RelationEntry> previous = book.entries();
        RelationBook.Change result = book.change(target, action, name, presence.players());
        finish(previous, result.changed, false);
        if (result.succeeded && !"remove".equals(action))
            lookups.retry(name);
        lookups.tick();
        return result.succeeded ? OperationResult.success(null, result.message)
                : OperationResult.failure(Status.INVALID_VALUE, result.message);
    }

    @Override
    public OperationResult<List<OperationResult<Void>>> changeMany(Relation target, String action,
            List<RelationEntry> entries) {
        client.check();
        String problem = readinessProblem();
        if (problem != null)
            return OperationResult.failure(Status.UNAVAILABLE, problem);
        Map<UUID, String> online = presence.players();
        List<RelationEntry> previous = book.entries();
        List<OperationResult<Void>> results = new ArrayList<>();
        boolean changed = false;
        for (RelationEntry entry : entries) {
            RelationBook.Change result =
                    entry.playerId() == null ? book.change(target, action, entry.name(), online)
                            : book.remove(target, entry.playerId());
            changed |= result.changed;
            results.add(result.succeeded ? OperationResult.success(null, result.message)
                    : OperationResult.failure(Status.INVALID_VALUE, result.message));
        }
        finish(previous, changed, false);
        if ("add".equals(action))
            for (RelationEntry entry : entries)
                lookups.retry(entry.name());
        lookups.tick();
        return OperationResult.success(results);
    }

    @Override
    public OperationResult<Void> replaceAll(List<RelationEntry> entries) {
        client.check();
        String problem = readinessProblem();
        if (problem != null)
            return OperationResult.failure(Status.UNAVAILABLE, problem);
        Set<UUID> ids = new HashSet<>();
        Set<String> pending = new HashSet<>();
        for (RelationEntry entry : entries) {
            if (entry.playerId() == null ? !pending.add(entry.name().toLowerCase(Locale.ROOT))
                    : !ids.add(entry.playerId())) {
                return OperationResult.failure(Status.INVALID_VALUE,
                        language.translate("Duplicate relation identity"));
            }
        }
        List<RelationEntry> previous = book.entries();
        book.replace(entries);
        observeOnline();
        finish(previous, true, false);
        lookups.tick();
        return OperationResult.success(null);
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        client.check();
        if (event.phase == Phase.START && started && ready)
            lookups.tick();
    }

    private boolean observeOnline() {
        boolean changed = false;
        for (Map.Entry<UUID, String> player : presence.players().entrySet()) {
            changed |= book.observe(player.getKey(), player.getValue());
        }
        return changed;
    }

    private void bindResolved(RelationEntry waiting, MojangProfileLookup.Profile profile) {
        if (!started || !ready)
            return;
        List<RelationEntry> previous = book.entries();
        String tabName = presence.players().get(profile.id);
        if (book.bind(waiting, profile.id, tabName == null ? profile.name : tabName))
            finish(previous, true, false);
    }

    private void finish(List<RelationEntry> previous, boolean changed, boolean loaded) {
        List<RelationEntry> current = book.entries();
        if (changed) {
            dirty = true;
            if (worker != null)
                worker.requestWrite(current);
        }
        lookups.refresh(current);
        Map<UUID, Relation> before = relationsById(previous);
        Map<UUID, Relation> after = relationsById(current);
        Set<UUID> ids = new LinkedHashSet<>(before.keySet());
        ids.addAll(after.keySet());
        for (UUID id : ids) {
            Relation oldRelation = before.getOrDefault(id, Relation.NONE);
            Relation newRelation = after.getOrDefault(id, Relation.NONE);
            if (oldRelation != newRelation)
                Listeners.notify(listeners,
                        listener -> listener.onRelationChanged(id, oldRelation, newRelation));
        }
        if (loaded)
            Listeners.notify(listeners, RelationListener::onRelationsLoaded);
        if (changed || loaded)
            notifyChanged();
    }

    private static Map<UUID, Relation> relationsById(List<RelationEntry> entries) {
        Map<UUID, Relation> result = new LinkedHashMap<>();
        for (RelationEntry entry : entries)
            if (entry.playerId() != null)
                result.put(entry.playerId(), entry.relation());
        return result;
    }

    private void dispatch(long expected, Runnable task) {
        client.execute(() -> {
            if (started && generation == expected)
                task.run();
        });
    }

    private final class IoListener implements RelationIoWorker.Listener {
        private final long expected;

        IoListener(long expected) {
            this.expected = expected;
        }

        @Override
        public void loaded(List<RelationEntry> entries) {
            dispatch(expected, () -> {
                book.replace(entries);
                loadProblem = null;
                ready = true;
                boolean changed = observeOnline();
                finish(Collections.emptyList(), changed, true);
                lookups.tick();
            });
        }

        @Override
        public void loadFailed(Exception failure) {
            LOGGER.log(Level.WARNING, "Failed to load relations from " + path, failure);
            dispatch(expected, () -> {
                String message = failure.getMessage();
                loadProblem =
                        message == null || message.isEmpty() ? failure.getClass().getSimpleName()
                                : message;
                notifyChanged();
            });
        }

        @Override
        public void writeFailed(IOException failure) {
            LOGGER.log(Level.WARNING, "Failed to save relations to " + path, failure);
            dispatch(expected, () -> {
                if (installAdapters && Minecraft.getMinecraft().thePlayer != null) {
                    ChatFeedback.reply(Minecraft.getMinecraft().thePlayer, Tone.ERROR,
                            "Relations could not be saved");
                }
            });
        }
    }
}
