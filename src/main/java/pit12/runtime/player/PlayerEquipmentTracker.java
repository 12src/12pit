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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import pit12.runtime.session.ClientSession;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.ClientLifecycle;

public final class PlayerEquipmentTracker implements ClientLifecycle, PlayerEquipmentAccess,
        PlayerEquipmentPacketObserver, PlayerEntityListener {
    private final Runnable onClientTickListener = this::onClientTick;

    private final ClientSession session;
    private final PlayerEntityTracker players;
    private final Runnable sessionChanged;
    private final PlayerEquipmentCache cache = new PlayerEquipmentCache();
    private final List<PlayerEquipmentListener> listeners = new ArrayList<>();
    private NetHandlerPlayClient boundNetHandler;
    private boolean started;

    public PlayerEquipmentTracker(ClientSession session, PlayerEntityTracker players) {
        this.session = session;
        this.players = players;
        sessionChanged = () -> bindNetHandler(session.connection());
    }

    @Override
    public void start() {
        session.checkThread();
        if (started) {
            return;
        }
        ClientEvents.TICK_START.add(onClientTickListener);
        started = true;
        players.addListener(this);
        session.addListener(sessionChanged);
        sessionChanged.run();
        onPlayerEntitiesReset();
    }

    @Override
    public void stop() {
        session.checkThread();
        if (!started) {
            return;
        }
        started = false;
        session.removeListener(sessionChanged);
        players.removeListener(this);
        unbindNetHandler();
        ClientEvents.TICK_START.remove(onClientTickListener);
        clearCache();
        listeners.clear();
    }

    @Override
    public PlayerEquipmentSnapshot loadedEquipment(UUID playerId) {
        session.checkThread();
        return cache.loadedEquipment(playerId);
    }

    @Override
    public void addListener(PlayerEquipmentListener listener) {
        session.checkThread();
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(PlayerEquipmentListener listener) {
        session.checkThread();
        listeners.remove(listener);
    }

    @Override
    public void onEquipmentPacket(int entityId, int equipmentSlot) {
        session.checkThread();
        int slots = slotsFor(equipmentSlot);
        if (slots == 0) {
            return;
        }
        WorldClient world = session.world();
        Entity entity = world == null ? null : world.getEntityByID(entityId);
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            UUID playerId = player.getUniqueID();
            if (players.loadedPlayer(playerId) != player) {
                return;
            }
            cache.markDirty(playerId, slots);
            ItemStack stack = equipmentSlot == 0 ? player.getHeldItem()
                    : player.getCurrentArmor(equipmentSlot - 1);
            Listeners.notify(listeners,
                    listener -> listener.onPlayerEquipmentReceived(playerId, stack));
        }
    }

    @Override
    public void onPlayerEntityRemoved(EntityPlayer player) {
        UUID playerId = player.getUniqueID();
        if (cache.remove(playerId) != null) {
            notifyRemoved(playerId);
        }
    }

    @Override
    public void onPlayerEntityAdded(EntityPlayer player) {
        cache.markDirty(player.getUniqueID(), PlayerEquipmentCache.ALL);
    }

    @Override
    public void onPlayerEntitiesReset() {
        clearCache();
        WorldClient world = session.world();
        if (world != null) {
            for (EntityPlayer player : world.playerEntities) {
                cache.markDirty(player.getUniqueID(), PlayerEquipmentCache.ALL);
            }
        }
    }

    private void onClientTick() {
        session.checkThread();
        session.refresh();
        if (session.world() == null) {
            clearCache();
            return;
        }
        applyDirty();
    }

    private void bindNetHandler(NetHandlerPlayClient netHandler) {
        if (boundNetHandler == netHandler) {
            return;
        }
        unbindNetHandler();
        if (netHandler instanceof PlayerEquipmentPacketBinding) {
            ((PlayerEquipmentPacketBinding) netHandler)
                    .bindPlayerEquipmentObserver((entityId, slot) -> {
                        session.refresh();
                        if (started && session.connection() == netHandler)
                            onEquipmentPacket(entityId, slot);
                    });
            boundNetHandler = netHandler;
        }
    }

    private void unbindNetHandler() {
        if (boundNetHandler instanceof PlayerEquipmentPacketBinding) {
            ((PlayerEquipmentPacketBinding) boundNetHandler).bindPlayerEquipmentObserver(null);
        }
        boundNetHandler = null;
    }

    private void applyDirty() {
        Map<UUID, Integer> pending = cache.drainDirty();
        if (pending.isEmpty()) {
            return;
        }
        List<Runnable> notifications = new ArrayList<>();
        for (Map.Entry<UUID, Integer> change : pending.entrySet()) {
            EntityPlayer player = players.loadedPlayer(change.getKey());
            if (player == null) {
                if (cache.remove(change.getKey()) != null) {
                    notifications.add(() -> notifyRemoved(change.getKey()));
                }
                continue;
            }
            PlayerEquipmentSnapshot snapshot = cache.observe(player, change.getValue());
            notifications.add(() -> notifyChanged(snapshot, change.getValue()));
        }
        notifications.forEach(Runnable::run);
    }

    private void clearCache() {
        boolean hadEquipment = !cache.isEmpty();
        cache.clear();
        if (hadEquipment)
            Listeners.notify(listeners, PlayerEquipmentListener::onPlayerEquipmentReset);
    }

    private void notifyChanged(PlayerEquipmentSnapshot snapshot, int changedSlots) {
        Listeners.notify(listeners, listener -> listener
                .onPlayerEquipmentChanged(snapshot.playerId(), changedSlots, snapshot.revision()));
    }

    private void notifyRemoved(UUID playerId) {
        Listeners.notify(listeners, listener -> listener.onPlayerEquipmentRemoved(playerId));
    }

    private static int slotsFor(int equipmentSlot) {
        if (equipmentSlot == 0) {
            return PlayerEquipmentCache.HELD_ITEM;
        }
        switch (equipmentSlot) {
            case 1:
                return PlayerEquipmentCache.BOOTS;
            case 2:
                return PlayerEquipmentCache.LEGGINGS;
            case 3:
                return PlayerEquipmentCache.CHESTPLATE;
            case 4:
                return PlayerEquipmentCache.HELMET;
            default:
                return 0;
        }
    }
}
