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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.runtime.session.ClientSession;
import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.text.PlayerNameFormatter;

/** Queries and subscriptions require the client thread. */
public final class PlayerNameCache
        implements ClientLifecycle, TabPresenceListener, PlayerTeamObserver {
    private final Minecraft minecraft;
    private final ClientSession session;
    private final TabPresence presence;
    private final Runnable sessionChanged = this::onSessionChanged;
    private final Map<UUID, NameEntry> entries = new HashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();
    private final Set<UUID> dirty = new LinkedHashSet<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private NetHandlerPlayClient connection;
    private Scoreboard scoreboard;
    private boolean started;

    public PlayerNameCache(Minecraft minecraft, ClientSession session, TabPresence presence) {
        this.minecraft = minecraft;
        this.session = session;
        this.presence = presence;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started) {
            return;
        }
        started = true;
        session.addListener(sessionChanged);
        presence.addListener(this);
        MinecraftForge.EVENT_BUS.register(this);
        onSessionChanged();
    }

    @Override
    public void stop() {
        session.checkThread();
        if (!started) {
            return;
        }
        started = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        presence.removeListener(this);
        session.removeListener(sessionChanged);
        if (scoreboard instanceof PlayerTeamBinding) {
            ((PlayerTeamBinding) scoreboard).bindPlayerTeamObserver(null);
        }
        scoreboard = null;
        connection = null;
        clearEntries();
        listeners.clear();
    }

    /** Returns null when the Tab entry is absent or its name is unknown. */
    public String displayName(UUID playerId) {
        session.checkThread();
        NameEntry entry = ready(playerId);
        return entry == null ? null : entry.displayName;
    }

    /** Returns null when the Tab entry is absent or its name is unknown. */
    public String shortName(UUID playerId) {
        session.checkThread();
        NameEntry entry = ready(playerId);
        if (entry == null || entry.displayName == null) {
            return null;
        }
        if (entry.shortName == null) {
            entry.shortName = PlayerNameFormatter.shorten(entry.displayName);
        }
        return entry.shortName;
    }

    public void addListener(Runnable listener) {
        session.checkThread();
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Runnable listener) {
        session.checkThread();
        listeners.remove(listener);
    }

    @Override
    public void onPlayerSeen(UUID playerId, String name, boolean joined) {
        NetHandlerPlayClient handler = session.connection();
        NetworkPlayerInfo info = handler == null ? null : handler.getPlayerInfo(playerId);
        if (info != null) {
            observe(info);
        }
    }

    @Override
    public void onPlayerLeft(UUID playerId) {
        session.checkThread();
        NameEntry entry = entries.remove(playerId);
        dirty.remove(playerId);
        if (entry != null) {
            ((PlayerNameBinding) entry.info).bindPlayerNameObserver(null);
            byName.remove(entry.info.getGameProfile().getName(), playerId);
            Listeners.notify(listeners, Runnable::run);
        }
    }

    @Override
    public void onTabDisplayChanged(UUID playerId) {
        session.checkThread();
        dirty.add(playerId);
    }

    @Override
    public void onTeamChanged(ScorePlayerTeam team) {
        session.checkThread();
        // Removing a team keeps its member collection after clearing scoreboard membership.
        for (String name : team.getMembershipCollection()) {
            onTeamMemberChanged(name);
        }
    }

    @Override
    public void onTeamMemberChanged(String name) {
        session.checkThread();
        UUID playerId = byName.get(name);
        if (playerId != null) {
            dirty.add(playerId);
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) {
            return;
        }
        session.checkThread();
        session.refresh();
        while (!dirty.isEmpty()) {
            UUID playerId = dirty.iterator().next();
            dirty.remove(playerId);
            updateName(playerId);
        }
    }

    private void onSessionChanged() {
        NetHandlerPlayClient handler = session.connection();
        if (connection != handler) {
            clearEntries();
            connection = handler;
            if (handler != null) {
                for (NetworkPlayerInfo info : handler.getPlayerInfoMap()) {
                    observe(info);
                }
            }
            Listeners.notify(listeners, Runnable::run);
        }
        WorldClient world = session.world();
        Scoreboard current = world == null ? null : world.getScoreboard();
        if (scoreboard != current) {
            if (scoreboard instanceof PlayerTeamBinding) {
                ((PlayerTeamBinding) scoreboard).bindPlayerTeamObserver(null);
            }
            scoreboard = current;
            if (current instanceof PlayerTeamBinding) {
                ((PlayerTeamBinding) current).bindPlayerTeamObserver(this);
            }
            dirty.addAll(entries.keySet());
        }
    }

    private void observe(NetworkPlayerInfo info) {
        UUID playerId = info.getGameProfile().getId();
        if (playerId == null) {
            return;
        }
        NameEntry entry = entries.get(playerId);
        if (entry == null || entry.info != info) {
            if (entry != null) {
                ((PlayerNameBinding) entry.info).bindPlayerNameObserver(null);
                byName.remove(entry.info.getGameProfile().getName(), playerId);
                entry.info = info;
            } else {
                entries.put(playerId, new NameEntry(info));
            }
            String name = info.getGameProfile().getName();
            if (name != null) {
                byName.put(name, playerId);
            }
            ((PlayerNameBinding) info).bindPlayerNameObserver(() -> {
                session.checkThread();
                NameEntry current = entries.get(playerId);
                if (started && current != null && current.info == info) {
                    dirty.add(playerId);
                }
            });
        }
        dirty.add(playerId);
    }

    private NameEntry ready(UUID playerId) {
        if (dirty.remove(playerId)) {
            updateName(playerId);
        }
        return entries.get(playerId);
    }

    private void updateName(UUID playerId) {
        NameEntry entry = entries.get(playerId);
        if (entry == null) {
            return;
        }
        NetworkPlayerInfo info = entry.info;
        String name;
        if (info.getDisplayName() == null && info.getGameProfile().getName() == null) {
            name = null;
        } else if (minecraft.theWorld == null) {
            // Vanilla's team lookup needs a world, but explicit Tab names do not.
            name = info.getDisplayName() == null ? info.getGameProfile().getName()
                    : info.getDisplayName().getFormattedText();
        } else {
            name = minecraft.ingameGUI.getTabList().getPlayerName(info);
        }
        if (!Objects.equals(entry.displayName, name)) {
            entry.displayName = name;
            entry.shortName = null;
            Listeners.notify(listeners, Runnable::run);
        }
    }

    private void clearEntries() {
        for (NameEntry entry : entries.values()) {
            ((PlayerNameBinding) entry.info).bindPlayerNameObserver(null);
        }
        entries.clear();
        byName.clear();
        dirty.clear();
    }

    private static final class NameEntry {
        private NetworkPlayerInfo info;
        private String displayName;
        private String shortName;

        private NameEntry(NetworkPlayerInfo info) {
            this.info = info;
        }
    }
}
