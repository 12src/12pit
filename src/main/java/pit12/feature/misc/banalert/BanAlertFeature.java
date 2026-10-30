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
package pit12.feature.misc.banalert;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.languages.Languages;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceListener;
import pit12.runtime.session.ClientSession;
import pit12.shared.chat.ChatFeedback;
import pit12.shared.chat.ChatFeedback.Tone;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.text.TextCodes;

public final class BanAlertFeature implements ClientLifecycle, TabPresenceListener {
    private static final String BAN_MESSAGE_PREFIX = "A player has been removed from your game";
    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1);
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final BanAlertConfig config;
    private final ClientSession session;
    private final TabPresence presence;
    private final Languages language;
    private final Map<UUID, String> onlineNames = new HashMap<>();
    private final Map<UUID, Departure> departures = new LinkedHashMap<>();
    private final Deque<Long> pendingMessages = new ArrayDeque<>();
    private final Runnable sessionChanged = this::reset;
    private final ConfigChangeListener configChanged = changes -> {
        if (changes.affects("ban_alert", "enabled")) {
            updateActivation();
        }
    };
    private long sessionRevision;
    private boolean started;
    private boolean active;

    public BanAlertFeature(Minecraft minecraft, ConfigCatalog configs, BanAlertConfig config,
            ClientSession session, TabPresence presence, Languages language) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.presence = presence;
        this.language = language;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started) {
            return;
        }
        started = true;
        configs.addListener(configChanged);
        updateActivation();
    }

    @Override
    public void stop() {
        session.checkThread();
        if (!started) {
            return;
        }
        started = false;
        configs.removeListener(configChanged);
        updateActivation();
    }

    private void updateActivation() {
        boolean enabled = started && config.enabled();
        if (active == enabled) {
            return;
        }
        active = enabled;
        if (active) {
            session.addListener(sessionChanged);
            presence.addListener(this);
            MinecraftForge.EVENT_BUS.register(this);
            reset();
        } else {
            MinecraftForge.EVENT_BUS.unregister(this);
            presence.removeListener(this);
            session.removeListener(sessionChanged);
            onlineNames.clear();
            departures.clear();
            pendingMessages.clear();
        }
    }

    @Override
    public void onPlayerSeen(UUID playerId, String name, boolean joined) {
        session.checkThread();
        if (!active || sessionRevision != session.revision()) {
            return;
        }
        if (name != null) {
            onlineNames.put(playerId, name);
        }
        departures.remove(playerId);
    }

    @Override
    public void onPlayerLeft(UUID playerId) {
        session.checkThread();
        // Tab reports departures on reset before our session listener runs.
        if (!active || sessionRevision != session.revision()) {
            return;
        }
        String name = onlineNames.remove(playerId);
        if (name == null) {
            return;
        }
        long now = System.nanoTime();
        discardExpired(now);
        departures.put(playerId, new Departure(name, now));
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!active || event.type == 2) {
            return;
        }
        String message = event.message.getUnformattedText();
        if (!message.contains(BAN_MESSAGE_PREFIX)
                || !TextCodes.strip(message).startsWith(BAN_MESSAGE_PREFIX)) {
            return;
        }
        session.refresh();
        if (session.world() == null) {
            return;
        }
        long now = System.nanoTime();
        pendingMessages.addLast(now);
        discardExpired(now);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (!active || event.phase != Phase.END || pendingMessages.isEmpty()) {
            return;
        }
        session.refresh();
        long now = System.nanoTime();
        if (pendingMessages.isEmpty() || now - pendingMessages.peekFirst() < WINDOW_NANOS) {
            return;
        }
        while (!pendingMessages.isEmpty() && now - pendingMessages.peekFirst() >= WINDOW_NANOS) {
            long messageTime = pendingMessages.removeFirst();
            Departure closest = null;
            long closestDifference = 0;
            for (Departure departure : departures.values()) {
                long difference = departure.time - messageTime;
                if (Math.abs(difference) <= WINDOW_NANOS && (closest == null
                        || Math.abs(difference) < Math.abs(closestDifference))) {
                    closest = departure;
                    closestDifference = difference;
                }
            }
            EntityPlayerSP player = minecraft.thePlayer;
            if (closest != null && player != null) {
                String offset = (closestDifference < 0 ? "-" : "+")
                        + TimeUnit.NANOSECONDS.toMillis(Math.abs(closestDifference));
                ChatFeedback.reply(player, Tone.WARNING, language.format(
                        "Possibly banned player: {0} §e({1}ms)", "§f" + closest.name, offset));
            }
        }
        discardExpired(now);
    }

    private void reset() {
        session.checkThread();
        sessionRevision = session.revision();
        onlineNames.clear();
        departures.clear();
        pendingMessages.clear();
        onlineNames.putAll(presence.players());
    }

    private void discardExpired(long now) {
        long cutoff = pendingMessages.isEmpty() ? now - WINDOW_NANOS
                : pendingMessages.peekFirst() - WINDOW_NANOS;
        // The oldest message still needs its candidates until its window closes.
        Iterator<Departure> iterator = departures.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().time >= cutoff) {
                break;
            }
            iterator.remove();
        }
    }

    private static final class Departure {
        private final String name;
        private final long time;

        private Departure(String name, long time) {
            this.name = name;
            this.time = time;
        }
    }
}
