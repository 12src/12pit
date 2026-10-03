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
package pit12.runtime.player;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S38PacketPlayerListItem.Action;
import net.minecraft.network.play.server.S38PacketPlayerListItem.AddPlayerData;
import pit12.runtime.session.ClientSession;
import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.ClientLifecycle;

/** All queries, subscriptions and packet observations run on the client thread. */
public final class TabPresenceTracker implements ClientLifecycle, TabPresence, TabPacketObserver {
    private final ClientSession session;
    private final Runnable sessionChanged = this::onSessionChanged;
    private final Set<UUID> present = new LinkedHashSet<>();
    private final List<TabPresenceListener> listeners = new ArrayList<>();
    private NetHandlerPlayClient boundHandler;
    private boolean started;

    public TabPresenceTracker(ClientSession session) {
        this.session = session;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started)
            return;
        started = true;
        session.addListener(sessionChanged);
        onSessionChanged();
    }

    @Override
    public void stop() {
        session.checkThread();
        if (!started)
            return;
        started = false;
        session.removeListener(sessionChanged);
        bind(null);
        listeners.clear();
    }

    @Override
    public boolean contains(UUID playerId) {
        session.checkThread();
        return present.contains(playerId);
    }

    @Override
    public Map<UUID, String> players() {
        session.checkThread();
        return currentPlayers();
    }

    private Map<UUID, String> currentPlayers() {
        Map<UUID, String> current = new LinkedHashMap<>();
        if (boundHandler != null) {
            for (NetworkPlayerInfo info : boundHandler.getPlayerInfoMap()) {
                GameProfile profile = info.getGameProfile();
                if (profile.getId() != null && profile.getName() != null) {
                    current.put(profile.getId(), profile.getName());
                }
            }
        }
        return current;
    }

    @Override
    public void addListener(TabPresenceListener listener) {
        session.checkThread();
        if (!listeners.contains(listener))
            listeners.add(listener);
    }

    @Override
    public void removeListener(TabPresenceListener listener) {
        session.checkThread();
        listeners.remove(listener);
    }

    @Override
    public void onTabPacket(S38PacketPlayerListItem packet) {
        session.checkThread();
        Action action = packet.getAction();
        if (action == Action.UPDATE_DISPLAY_NAME) {
            Listeners.notify(listeners, TabPresenceListener::onTabDisplayChanged);
            return;
        }
        if (action != Action.ADD_PLAYER && action != Action.REMOVE_PLAYER)
            return;
        List<Runnable> notifications = new ArrayList<>();
        for (AddPlayerData entry : packet.getEntries()) {
            UUID id = entry.getProfile().getId();
            if (id == null)
                continue;
            if (action == Action.REMOVE_PLAYER) {
                if (present.remove(id))
                    notifications.add(() -> Listeners.notify(listeners,
                            listener -> listener.onPlayerLeft(id)));
            } else {
                NetworkPlayerInfo info = boundHandler.getPlayerInfo(id);
                if (info != null) {
                    String name = info.getGameProfile().getName();
                    boolean joined = present.add(id);
                    notifications.add(() -> Listeners.notify(listeners,
                            listener -> listener.onPlayerSeen(id, name, joined)));
                }
            }
        }
        notifications.forEach(Runnable::run);
    }

    private void onSessionChanged() {
        bind(session.connection());
    }

    private void bind(NetHandlerPlayClient handler) {
        if (boundHandler == handler)
            return;
        if (boundHandler instanceof TabPacketBinding)
            ((TabPacketBinding) boundHandler).bindTabObserver(null);
        Set<UUID> previous = new LinkedHashSet<>(present);
        present.clear();
        boundHandler = handler;
        if (handler instanceof TabPacketBinding) {
            ((TabPacketBinding) handler).bindTabObserver(packet -> {
                session.refresh();
                if (started && boundHandler == handler && session.connection() == handler)
                    onTabPacket(packet);
            });
        }
        if (handler != null) {
            for (NetworkPlayerInfo info : handler.getPlayerInfoMap()) {
                if (info.getGameProfile().getId() != null)
                    present.add(info.getGameProfile().getId());
            }
        }
        Map<UUID, String> current = currentPlayers();
        for (UUID id : previous)
            Listeners.notify(listeners, listener -> listener.onPlayerLeft(id));
        current.forEach((id, name) -> Listeners.notify(listeners,
                listener -> listener.onPlayerSeen(id, name, true)));
    }
}
