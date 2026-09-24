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
package pit12.feature.player;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S38PacketPlayerListItem.Action;
import net.minecraft.network.play.server.S38PacketPlayerListItem.AddPlayerData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import pit12.feature.Feature;
import pit12.runtime.player.TabPacketBinding;
import pit12.runtime.player.TabPacketObserver;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceListener;

public final class PlayerPresenceFeature implements Feature, TabPresence, TabPacketObserver {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Set<UUID> present = new LinkedHashSet<UUID>();
    private final List<TabPresenceListener> listeners = new ArrayList<TabPresenceListener>();
    private NetHandlerPlayClient boundHandler;
    private NetHandlerPlayClient disconnectedHandler;
    private boolean started;

    @Override
    public void start() {
        if (started) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        started = true;
        bind(minecraft.getNetHandler());
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        bind(null);
        disconnectedHandler = null;
        listeners.clear();
    }

    @Override
    public boolean contains(UUID playerId) {
        return present.contains(playerId);
    }

    @Override
    public Map<UUID, String> players() {
        if (boundHandler == null) {
            return Collections.emptyMap();
        }
        Map<UUID, String> current = new LinkedHashMap<UUID, String>();
        for (NetworkPlayerInfo info : boundHandler.getPlayerInfoMap()) {
            GameProfile profile = info.getGameProfile();
            if (profile.getId() != null && profile.getName() != null) {
                current.put(profile.getId(), profile.getName());
            }
        }
        return Collections.unmodifiableMap(current);
    }

    @Override
    public void addListener(TabPresenceListener listener) {
        if (!listeners.contains(Objects.requireNonNull(listener, "listener"))) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(TabPresenceListener listener) {
        listeners.remove(listener);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase == Phase.START) {
            NetHandlerPlayClient handler = minecraft.getNetHandler();
            if (handler == null || handler != disconnectedHandler) {
                disconnectedHandler = null;
                bind(handler);
            }
        }
    }

    @SubscribeEvent
    public void onDisconnect(ClientDisconnectionFromServerEvent event) {
        // The old handler can remain accessible for a tick after disconnect.
        disconnectedHandler = boundHandler;
        bind(null);
    }

    @Override
    public void onTabPacket(S38PacketPlayerListItem packet) {
        Action action = packet.getAction();
        if (action == Action.UPDATE_DISPLAY_NAME) {
            for (TabPresenceListener listener : new ArrayList<TabPresenceListener>(listeners)) {
                listener.onTabDisplayChanged();
            }
            return;
        }
        if (action != Action.ADD_PLAYER && action != Action.REMOVE_PLAYER) {
            return;
        }
        for (AddPlayerData entry : packet.getEntries()) {
            GameProfile profile = entry.getProfile();
            UUID id = profile.getId();
            if (id == null) {
                continue;
            }
            if (action == Action.REMOVE_PLAYER) {
                if (present.remove(id)) {
                    for (TabPresenceListener listener : new ArrayList<TabPresenceListener>(
                            listeners)) {
                        listener.onPlayerLeft(id);
                    }
                }
            } else {
                NetworkPlayerInfo info = boundHandler.getPlayerInfo(id);
                if (info != null) {
                    String name = info.getGameProfile().getName();
                    boolean joined = present.add(id);
                    for (TabPresenceListener listener : new ArrayList<TabPresenceListener>(
                            listeners)) {
                        listener.onPlayerSeen(id, name, joined);
                    }
                }
            }
        }
    }

    private void bind(NetHandlerPlayClient handler) {
        if (boundHandler == handler) {
            return;
        }
        if (boundHandler instanceof TabPacketBinding) {
            ((TabPacketBinding) boundHandler).bindTabObserver(null);
        }
        boundHandler = null;
        for (UUID id : new LinkedHashSet<UUID>(present)) {
            present.remove(id);
            for (TabPresenceListener listener : new ArrayList<TabPresenceListener>(listeners)) {
                listener.onPlayerLeft(id);
            }
        }
        if (handler == null) {
            return;
        }
        boundHandler = handler;
        if (handler instanceof TabPacketBinding) {
            ((TabPacketBinding) handler).bindTabObserver(this);
        }
        for (NetworkPlayerInfo info : handler.getPlayerInfoMap()) {
            GameProfile profile = info.getGameProfile();
            if (profile.getId() != null) {
                boolean joined = present.add(profile.getId());
                for (TabPresenceListener listener : new ArrayList<TabPresenceListener>(listeners)) {
                    listener.onPlayerSeen(profile.getId(), profile.getName(), joined);
                }
            }
        }
    }
}
