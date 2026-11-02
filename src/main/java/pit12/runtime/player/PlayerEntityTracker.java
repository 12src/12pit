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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import pit12.runtime.session.ClientSession;
import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.ClientLifecycle;

/** Use this API on the client thread. Returned entities are live. */
public final class PlayerEntityTracker implements ClientLifecycle, PlayerEntityWorldObserver {
    private final ClientSession session;
    private final Runnable sessionChanged = this::onSessionChanged;
    private final Map<UUID, EntityPlayer> players = new HashMap<>();
    private final Listeners<PlayerEntityListener> listeners = new Listeners<>();
    private WorldClient boundWorld;
    private boolean started;

    public PlayerEntityTracker(ClientSession session) {
        this.session = session;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started) {
            return;
        }
        started = true;
        session.addListener(sessionChanged);
        onSessionChanged();
    }

    @Override
    public void stop() {
        session.checkThread();
        if (!started) {
            return;
        }
        started = false;
        session.removeListener(sessionChanged);
        unbindWorld();
        players.clear();
        listeners.clear();
    }

    /** Returns null if the player's current entity is unloaded or removed. */
    public EntityPlayer loadedPlayer(UUID playerId) {
        session.checkThread();
        EntityPlayer player = players.get(playerId);
        return player == null || player.isDead ? null : player;
    }

    /** Listeners run after the index is updated. Remove them when their owner stops. */
    public void addListener(PlayerEntityListener listener) {
        session.checkThread();
        listeners.add(listener);
    }

    public void removeListener(PlayerEntityListener listener) {
        session.checkThread();
        listeners.remove(listener);
    }

    @Override
    public void onPlayerAdded(EntityPlayer player) {
        session.checkThread();
        session.refresh();
        if (!started || player.worldObj != boundWorld || player.isDead) {
            return;
        }
        EntityPlayer previous = players.put(player.getUniqueID(), player);
        if (previous == player) {
            return;
        }
        if (previous != null) {
            listeners.notify(listener -> listener.onPlayerEntityRemoved(previous));
        }
        listeners.notify(listener -> listener.onPlayerEntityAdded(player));
    }

    @Override
    public void onPlayerRemoved(EntityPlayer player) {
        session.checkThread();
        session.refresh();
        if (!started || player.worldObj != boundWorld) {
            return;
        }
        UUID playerId = player.getUniqueID();
        // The old entity may be removed after its replacement was indexed.
        if (players.get(playerId) != player) {
            return;
        }
        players.remove(playerId);
        listeners.notify(listener -> listener.onPlayerEntityRemoved(player));
    }

    private void onSessionChanged() {
        WorldClient world = session.world();
        if (boundWorld == world) {
            return;
        }
        unbindWorld();
        players.clear();
        boundWorld = world;
        if (world instanceof PlayerEntityWorldBinding) {
            ((PlayerEntityWorldBinding) world).bindPlayerEntityObserver(this);
        }
        if (world != null) {
            for (Entity entity : world.loadedEntityList) {
                if (entity instanceof EntityPlayer && !entity.isDead) {
                    players.put(entity.getUniqueID(), (EntityPlayer) entity);
                }
            }
        }
        listeners.notify(PlayerEntityListener::onPlayerEntitiesReset);
    }

    private void unbindWorld() {
        if (boundWorld instanceof PlayerEntityWorldBinding) {
            ((PlayerEntityWorldBinding) boundWorld).bindPlayerEntityObserver(null);
        }
        boundWorld = null;
    }
}
