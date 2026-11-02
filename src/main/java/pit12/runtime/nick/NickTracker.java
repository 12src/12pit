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
package pit12.runtime.nick;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import pit12.runtime.player.PlayerNameCache;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceListener;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.text.TextCodes;

/** Use this API on the client thread. */
public final class NickTracker implements ClientLifecycle, TabPresenceListener {
    private static final Pattern NPC_TAB_NAME = Pattern.compile("[a-z0-9]{10}(?![A-Za-z0-9_])");
    private final ClientThread client;
    private final TabPresence presence;
    private final PlayerNameCache names;
    private final Set<UUID> checked = new HashSet<>();
    private final Map<UUID, String> nicked = new HashMap<>();
    private final Listeners<NickListener> listeners = new Listeners<>();
    private boolean started;

    public NickTracker(ClientThread client, TabPresence presence, PlayerNameCache names) {
        this.client = client;
        this.presence = presence;
        this.names = names;
    }

    @Override
    public void start() {
        client.check();
        if (started) {
            return;
        }
        started = true;
        presence.addListener(this);
        presence.players().forEach((id, name) -> onPlayerSeen(id, name, true));
    }

    @Override
    public void stop() {
        client.check();
        started = false;
        presence.removeListener(this);
        for (UUID playerId : nicked.keySet()) {
            names.bindDisplayNameObserver(playerId, null);
        }
        nicked.clear();
        checked.clear();
        listeners.clear();
    }

    public boolean isNicked(UUID playerId) {
        client.check();
        return nicked.containsKey(playerId);
    }

    public void addListener(NickListener listener) {
        client.check();
        listeners.add(listener);
    }

    public void removeListener(NickListener listener) {
        client.check();
        listeners.remove(listener);
    }

    @Override
    public void onPlayerSeen(UUID playerId, String name, boolean joined) {
        if (name == null || (playerId.version() == 4 && playerId.variant() == 2)
                || !checked.add(playerId)) {
            return;
        }
        String display = names.shortName(playerId);
        if (display == null || isNpc(display)) {
            return;
        }
        nicked.put(playerId, name);
        names.bindDisplayNameObserver(playerId, updated -> {
            if (updated != null && isNpc(updated)) {
                // Stay in checked, or a repeated ADD packet marks this NPC as Nick again.
                nicked.remove(playerId);
                names.bindDisplayNameObserver(playerId, null);
                listeners.notify(listener -> listener.onNickChanged(playerId, name, false));
            }
        });
        listeners.notify(listener -> listener.onNickChanged(playerId, name, true));
    }

    @Override
    public void onPlayerLeft(UUID playerId) {
        checked.remove(playerId);
        String name = nicked.remove(playerId);
        if (name != null) {
            names.bindDisplayNameObserver(playerId, null);
            listeners.notify(listener -> listener.onNickChanged(playerId, name, false));
        }
    }

    private boolean isNpc(String display) {
        String plain = TextCodes.strip(display).trim();
        return plain.contains("[NPC]") || NPC_TAB_NAME.matcher(display).lookingAt();
    }
}
