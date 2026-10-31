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
package pit12.feature.misc.denick;

import static net.minecraft.util.EnumChatFormatting.WHITE;
import static pit12.runtime.languages.Languages.source;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pit12.feature.misc.denick.api.DenickNames;
import pit12.runtime.command.CommandNode;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.languages.Languages;
import pit12.runtime.nick.NickListener;
import pit12.runtime.nick.NickTracker;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.PitState;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentCache;
import pit12.runtime.player.PlayerEquipmentListener;
import pit12.runtime.player.PlayerEquipmentSnapshot;
import pit12.runtime.player.PlayerNameCache;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceListener;
import pit12.runtime.session.ClientSession;
import pit12.shared.chat.ChatFeedback;
import pit12.shared.chat.ChatFeedback.Tone;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.ClientLifecycle;

public final class DenickFeature implements ClientLifecycle, DenickNames, TabPresenceListener,
        PlayerEquipmentListener, ConfigChangeListener, NickListener {
    private static final Logger LOGGER = LogManager.getLogger(DenickFeature.class);
    private static final Pattern PRESTIGE_PREFIX = Pattern.compile("\\[(?:§.|[IVXLCDM])+-");
    private final Minecraft minecraft;
    private final ClientThread client;
    private final ConfigCatalog configs;
    private final DenickConfig config;
    private final CommandRegistry commands;
    private final ClientSession session;
    private final PitContext pit;
    private final TabPresence presence;
    private final PlayerNameCache names;
    private final NickTracker nicks;
    private final PlayerEntityTracker players;
    private final PlayerEquipmentAccess equipment;
    private final Languages language;
    private final DenickNamesBinding binding;
    private final DenickStore store;
    private final Map<UUID, Target> targets = new LinkedHashMap<>();
    private final Map<String, String> mappings = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Runnable sessionChanged = this::resetWorld;
    private ExecutorService queryWorker;
    private ExecutorService storageWorker;
    private PitPalClient api;
    private boolean ready;
    private boolean writable;
    private boolean inPit;
    private boolean active;
    private boolean monitoring;
    private volatile long generation;
    private volatile boolean started;

    public DenickFeature(Minecraft minecraft, ClientThread client, ConfigCatalog configs,
            DenickConfig config, ClientSession session, PitContext pit, TabPresence presence,
            PlayerNameCache names, NickTracker nicks, PlayerEntityTracker players,
            PlayerEquipmentAccess equipment, CommandRegistry commands, Languages language,
            Path path, DenickNamesBinding binding) {
        this.minecraft = minecraft;
        this.client = client;
        this.configs = configs;
        this.config = config;
        this.commands = commands;
        this.session = session;
        this.pit = pit;
        this.presence = presence;
        this.names = names;
        this.nicks = nicks;
        this.players = players;
        this.equipment = equipment;
        this.language = language;
        this.binding = binding;
        store = new DenickStore(path);
        commands.register(CommandNode.command("denick", source("Find a nicked player's name"))
                .aliases("dn").arguments("<player> [force]", 1, 2).executes(this::command)
                .suggests((sender, args) -> args.length == 1
                        ? new ArrayList<>(presence.players().values())
                        : Collections.singletonList("force"))
                .build(), config, true);
    }

    @Override
    public void start() {
        client.check();
        if (started) {
            return;
        }
        started = true;
        configs.addListener(this);
        updateActivation();
        commands.setActive(config, true);
    }

    private void loadCache() {
        ready = false;
        writable = false;
        api = new PitPalClient();
        queryWorker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "12pit-denick-query");
            thread.setDaemon(true);
            return thread;
        });
        storageWorker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "12pit-denick-cache");
            thread.setDaemon(true);
            return thread;
        });
        ExecutorService loader = storageWorker;
        loader.execute(() -> {
            Map<String, String> loaded = null;
            try {
                loaded = store.load();
            } catch (IOException | RuntimeException failure) {
                LOGGER.warn("Cannot load denick cache", failure);
            }
            Map<String, String> result = loaded;
            client.execute(() -> {
                if (!started || storageWorker != loader) {
                    return;
                }
                mappings.clear();
                if (result != null) {
                    mappings.putAll(result);
                    writable = true;
                }
                ready = true;
            });
        });
    }

    private void updateActivation() {
        boolean enabled = started && config.enabled();
        if (active != enabled) {
            active = enabled;
            if (active) {
                if (storageWorker == null) {
                    loadCache();
                }
                nicks.addListener(this);
                presence.addListener(this);
                session.addListener(sessionChanged);
                MinecraftForge.EVENT_BUS.register(this);
                resetWorld();
                presence.players().forEach((id, name) -> onPlayerSeen(id, name, true));
            } else {
                MinecraftForge.EVENT_BUS.unregister(this);
                session.removeListener(sessionChanged);
                presence.removeListener(this);
                nicks.removeListener(this);
                // noinspection NonAtomicOperationOnVolatileField
                generation++;
                for (Target target : targets.values()) {
                    target.cancel();
                }
                targets.clear();
                inPit = false;
                changed();
            }
        }
        boolean monitor = active && config.automatic.get() && config.monitor.get();
        if (monitoring != monitor) {
            monitoring = monitor;
            if (monitoring) {
                equipment.addListener(this);
            } else {
                equipment.removeListener(this);
            }
        }
        binding.pit12$bindDenickNames(active && config.nickMarker.get() ? this : null);
    }

    @Override
    public void stop() {
        client.check();
        started = false;
        commands.setActive(config, false);
        configs.removeListener(this);
        updateActivation();
        if (queryWorker != null) {
            queryWorker.shutdownNow();
            queryWorker = null;
        }
        if (storageWorker != null) {
            storageWorker.shutdown();
            try {
                if (!storageWorker.awaitTermination(2000L, TimeUnit.MILLISECONDS)) {
                    storageWorker.shutdownNow();
                    LOGGER.warn("Denick cache worker did not stop");
                }
            } catch (InterruptedException interrupted) {
                storageWorker.shutdownNow();
                Thread.currentThread().interrupt();
            }
            storageWorker = null;
        }
        ready = false;
        mappings.clear();
        listeners.clear();
    }

    private void command(ICommandSender sender, String[] args) {
        client.check();
        if (!ready) {
            throw new IllegalArgumentException("Denick cache is loading");
        }
        if (args.length == 2 && !"force".equalsIgnoreCase(args[1])) {
            throw new IllegalArgumentException("Use force as the second argument");
        }
        if (session.world() == null) {
            throw new IllegalArgumentException("Join a world before using denick");
        }
        Map.Entry<UUID, String> player = presence.players().entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(args[0])).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player is not in Tab"));
        Target target = targets.get(player.getKey());
        if (args.length == 1 && (target == null || !target.nick)) {
            throw new IllegalArgumentException("Player is not nicked. Use force to query");
        }
        if (target == null) {
            target = new Target(player.getKey(), player.getValue());
            targets.put(player.getKey(), target);
        }
        begin(target, sender, args.length == 2);
        ChatFeedback.reply(sender, Tone.INFO,
                language.format("Querying {0}...", Tone.INFO.argument(WHITE, target.name)));
    }

    private void resetWorld() {
        client.check();
        // noinspection NonAtomicOperationOnVolatileField
        generation++;
        targets.replaceAll((id, previous) -> {
            previous.cancel();
            Target target = new Target(id, previous.name);
            target.nick = previous.nick;
            return target;
        });
        if (config.automatic.get()) {
            for (Target target : targets.values()) {
                if (target.nick) {
                    begin(target, null, false);
                }
            }
        }
        inPit = false;
        changed();
    }

    @Override
    public void onPlayerSeen(UUID playerId, String name, boolean joined) {
        if (name == null || !nicks.isNicked(playerId)) {
            return;
        }
        Target target = targets.computeIfAbsent(playerId, id -> new Target(id, name));
        // Tab and NickTracker both report the same player.
        if (target.nick) {
            return;
        }
        target.nick = true;
        if (config.automatic.get()) {
            begin(target, null, false);
        }
        changed();
    }

    @Override
    public void onNickChanged(UUID playerId, String name, boolean nicked) {
        if (nicked) {
            onPlayerSeen(playerId, name, true);
        } else {
            onPlayerLeft(playerId);
        }
    }

    @Override
    public void onPlayerLeft(UUID playerId) {
        Target target = targets.remove(playerId);
        if (target != null) {
            target.cancel();
            changed();
        }
    }

    @Override
    public void onPlayerEquipmentReceived(UUID playerId, ItemStack stack) {
        Target target = targets.get(playerId);
        if (target != null && target.automatic && target.search != null) {
            enqueue(target.search, stack);
        }
    }

    @Override
    public void onPlayerEquipmentChanged(UUID playerId, int changedSlots, long revision) {
        Target target = targets.get(playerId);
        if (target == null || !target.automatic || target.search == null) {
            return;
        }
        PlayerEquipmentSnapshot snapshot = equipment.loadedEquipment(playerId);
        if (snapshot != null) {
            for (int slot = 0; slot < 5; slot++) {
                if ((changedSlots & PlayerEquipmentCache.slotsFor(slot)) != 0) {
                    enqueue(target.search, snapshot.copyEquipment(slot));
                }
            }
        }
    }

    private void enqueue(Search search, ItemStack stack) {
        DenickItem item = DenickItem.read(stack);
        if (item != null) {
            search.pending.putIfAbsent(item.query, item);
        }
    }

    @Override
    public void onPlayerEquipmentRemoved(UUID playerId) {
        // We can still query the queued equipment after the entity unloads.
    }

    @Override
    public void onPlayerEquipmentReset() {
        // resetWorld clears the queues when the session changes.
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started) {
            return;
        }
        if (changes.affects("denick", "enabled") || changes.affects("denick", "automatic")
                || changes.affects("denick", "monitor")
                || changes.affects("denick", "nick_marker")) {
            updateActivation();
        }
        if (!active) {
            return;
        }
        if (changes.affects("denick", "automatic") && !config.automatic.get()) {
            for (Target target : targets.values()) {
                if (target.automatic && target.search != null) {
                    target.cancel();
                    target.attempted = false;
                }
            }
        }
        if (changes.affects("denick", "monitor")) {
            for (Target target : targets.values()) {
                if (!target.automatic || target.confirmed) {
                    continue;
                }
                if (config.monitor.get() && config.automatic.get() && target.search == null) {
                    begin(target, null, false);
                } else if (!config.monitor.get() && target.search != null
                        && !target.search.initial) {
                    target.cancel();
                }
            }
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (!active || event.phase != Phase.END) {
            return;
        }
        client.check();
        inPit = pit.current().pitState() == PitState.IN_PIT;
        if (!ready) {
            return;
        }
        for (Target target : targets.values()) {
            if (inPit && config.automatic.get() && target.nick && !target.attempted) {
                begin(target, null, false);
            }
            Search search = target.search;
            if (search != null && !search.busy && (search.initial || !search.pending.isEmpty())
                    && (!target.automatic || !search.initial || inPit)) {
                query(target);
            }
        }
    }

    private List<DenickItem> currentItems(UUID id) {
        EntityPlayer player = players.loadedPlayer(id);
        Map<String, DenickItem> items = new LinkedHashMap<>();
        if (player != null) {
            for (int slot = 0; slot < 5; slot++) {
                ItemStack stack =
                        slot == 0 ? player.getHeldItem() : player.getCurrentArmor(slot - 1);
                DenickItem item = DenickItem.read(stack);
                if (item != null) {
                    items.putIfAbsent(item.query, item);
                }
            }
        }
        return new ArrayList<>(items.values());
    }

    private void begin(Target target, ICommandSender sender, boolean force) {
        target.cancel();
        target.automatic = sender == null;
        target.attempted = true;
        target.confirmed = false;
        target.search = new Search(currentItems(target.id), sender, force);
    }

    private void query(Target target) {
        Search search = target.search;
        boolean first = search.initial;
        List<DenickItem> items = new ArrayList<>();
        int count = first ? search.initialCount : search.pending.size();
        for (DenickItem item : search.pending.values()) {
            if (items.size() == count) {
                break;
            }
            items.add(item);
        }
        items.sort(Comparator.comparingInt(item -> item.nonce >= 10 ? 0 : 1));
        String cached = mappings.get(target.name.toLowerCase(Locale.ROOT));
        search.busy = true;
        long requestGeneration = generation;
        // noinspection NonAtomicOperationOnVolatileField
        long request = ++target.request;
        BooleanSupplier cancelled = () -> !started || generation != requestGeneration
                || target.request != request || Thread.currentThread().isInterrupted();
        target.task = queryWorker.submit(() -> {
            String cachedDisplayName = null;
            try {
                if (first && !search.force && cached != null) {
                    try {
                        cachedDisplayName = api.displayName(cached, cancelled);
                        String name = cachedDisplayName;
                        client.execute(() -> {
                            if (generation != requestGeneration || target.request != request) {
                                return;
                            }
                            String display = displayName(target);
                            if (valid(target, requestGeneration, request)) {
                                target.setRealDisplayName(name);
                                target.owner = cached;
                                reply(search.sender, Tone.SUCCESS,
                                        language.format("{0} §c-> {1} §c(cached)",
                                                Tone.SUCCESS.argument(WHITE, display),
                                                Tone.SUCCESS.argument(WHITE, name)));
                                changed();
                            }
                        });
                    } catch (IOException failure) {
                        LOGGER.debug("Could not resolve cached denick owner for {}", target.name,
                                failure);
                    }
                }
                DenickQuery result = DenickQuery.run(api, items, search.force ? null : cached,
                        cachedDisplayName, cancelled);
                client.execute(() -> {
                    if (generation != requestGeneration || target.request != request) {
                        return;
                    }
                    String display = displayName(target);
                    if (valid(target, requestGeneration, request)) {
                        complete(target, search, items, cached, result, display);
                    }
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private boolean valid(Target target, long requestGeneration, long request) {
        return started && generation == requestGeneration && target.request == request
                && targets.get(target.id) == target && presence.contains(target.id);
    }

    private void complete(Target target, Search search, List<DenickItem> items, String cached,
            DenickQuery result, String display) {
        boolean first = search.initial;
        search.initial = false;
        search.busy = false;
        target.task = null;
        items.forEach(item -> search.pending.remove(item.query));
        if (result.displayName != null) {
            String previousName = target.realDisplayName;
            String previousOwner = target.owner;
            target.owner = result.owner;
            target.setRealDisplayName(result.displayName);
            target.confirmed = true;
            search.pending.clear();
            target.search = null;
            if (search.force || !Objects.equals(previousName, result.displayName)
                    || !Objects.equals(previousOwner, result.owner)) {
                boolean corrected = cached != null && !Objects.equals(cached, result.owner);
                reply(search.sender, Tone.SUCCESS,
                        corrected
                                ? language.format("{0} §c-> {1} §c(updated)",
                                        Tone.SUCCESS.argument(WHITE, display),
                                        Tone.SUCCESS.argument(WHITE, result.displayName))
                                : Tone.SUCCESS.argument(WHITE, display) + " §c-> "
                                        + Tone.SUCCESS.argument(WHITE, result.displayName));
                changed();
            }
            if (!Objects.equals(mappings.put(target.name.toLowerCase(Locale.ROOT), result.owner),
                    result.owner)) {
                persist();
            }
            return;
        }
        if (first && !items.isEmpty() && result.failure == null) {
            if (mappings.remove(target.name.toLowerCase(Locale.ROOT)) != null) {
                persist();
                if (search.sender != null || !config.successOnly.get()) {
                    reply(search.sender, Tone.WARNING,
                            language.format("Cached identity for {0} is no longer confirmed",
                                    Tone.WARNING.argument(WHITE, target.name)));
                }
            }
            target.setRealDisplayName(null);
            target.owner = null;
            changed();
        }
        target.confirmed = false;
        boolean monitoring = target.automatic && config.monitor.get();
        if (!monitoring) {
            search.pending.clear();
            target.search = null;
        }
        if (first && (search.sender != null || !config.successOnly.get())) {
            String message = items.isEmpty() ? source("No searchable equipment for {0}")
                    : result.failure != null ? source("Could not query {0}")
                            : source("No confirmed owner for {0}");
            String text = language.format(message, Tone.WARNING.argument(WHITE, target.name));
            if (monitoring) {
                text += ". " + language.translate("Waiting for new equipment");
            }
            reply(search.sender, Tone.WARNING, text);
        }
    }

    private void persist() {
        if (!writable) {
            return;
        }
        Map<String, String> saved = new LinkedHashMap<>(mappings);
        storageWorker.execute(() -> {
            try {
                store.write(saved);
            } catch (IOException | RuntimeException failure) {
                LOGGER.warn("Cannot save denick cache", failure);
            }
        });
    }

    private void reply(ICommandSender sender, Tone tone, String message) {
        ICommandSender recipient = sender == null ? minecraft.thePlayer : sender;
        if (recipient != null) {
            ChatFeedback.reply(recipient, tone, message);
        }
    }

    private String displayName(Target target) {
        // Refreshing the name can remove an NPC target before a query reply.
        String displayName = names.shortName(target.id);
        return displayName == null ? target.name : displayName;
    }

    @Override
    public String listName(UUID playerId, String original) {
        client.check();
        return nicks.isNicked(playerId) ? original.trim() + " §e§lN" : original;
    }

    @Override
    public String realDisplayName(UUID playerId) {
        client.check();
        if (!active) {
            return null;
        }
        Target target = targets.get(playerId);
        return target != null && target.nick ? target.shortRealDisplayName : null;
    }

    @Override
    public void addListener(Runnable listener) {
        client.check();
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(Runnable listener) {
        client.check();
        listeners.remove(listener);
    }

    private void changed() {
        Listeners.notify(listeners, Runnable::run);
    }

    private static final class Search {
        final Map<String, DenickItem> pending = new LinkedHashMap<>();
        final ICommandSender sender;
        final boolean force;
        final int initialCount;
        boolean initial = true;
        boolean busy;

        Search(List<DenickItem> items, ICommandSender sender, boolean force) {
            items.forEach(item -> pending.put(item.query, item));
            this.sender = sender;
            this.force = force;
            // New equipment shouldn't hold up the initial failure message.
            initialCount = pending.size();
        }
    }
    private static final class Target {
        final UUID id;
        final String name;
        boolean nick;
        boolean attempted;
        boolean automatic;
        boolean confirmed;
        volatile long request;
        String owner;
        String realDisplayName;
        String shortRealDisplayName;
        Search search;
        Future<?> task;

        Target(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        void setRealDisplayName(String name) {
            realDisplayName = name;
            shortRealDisplayName =
                    name == null ? null : PRESTIGE_PREFIX.matcher(name).replaceFirst("[");
        }

        void cancel() {
            // noinspection NonAtomicOperationOnVolatileField
            request++;
            if (task != null) {
                task.cancel(true);
                task = null;
            }
            if (search != null) {
                search.pending.clear();
                search = null;
            }
        }
    }
}
