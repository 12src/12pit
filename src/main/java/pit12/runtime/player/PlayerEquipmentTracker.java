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
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.runtime.session.ClientSession;
import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.ClientLifecycle;

public final class PlayerEquipmentTracker implements ClientLifecycle, PlayerEquipmentAccess,
        PlayerEquipmentPacketObserver, PlayerEquipmentWorldObserver {
    private final ClientSession session;
    private final Runnable sessionChanged = this::onSessionChanged;
    private final PlayerEquipmentCache cache = new PlayerEquipmentCache();
    private final List<PlayerEquipmentListener> listeners =
            new ArrayList<PlayerEquipmentListener>();
    private NetHandlerPlayClient boundNetHandler;
    private WorldClient boundWorld;
    private boolean started;

    public PlayerEquipmentTracker(ClientSession session) {
        this.session = session;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        started = true;
        session.addListener(sessionChanged);
        onSessionChanged();
    }

    @Override
    public void stop() {
        session.checkThread();
        if (!started) {
            clearCache();
            return;
        }
        started = false;
        session.removeListener(sessionChanged);
        unbindNetHandler();
        unbindWorld();
        MinecraftForge.EVENT_BUS.unregister(this);
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
        PlayerEquipmentListener nonNullListener = Objects.requireNonNull(listener, "listener");
        if (!listeners.contains(nonNullListener)) {
            listeners.add(nonNullListener);
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
            cache.markDirty(entity.getUniqueID(), slots);
        }
    }

    @Override
    public void onEntityRemoved(Entity entity) {
        session.checkThread();
        if (entity instanceof EntityPlayer) {
            UUID playerId = entity.getUniqueID();
            if (cache.remove(playerId) != null) {
                notifyRemoved(playerId);
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!event.world.isRemote)
            return;
        session.checkThread();
        if (event.world != session.world() || !(event.entity instanceof EntityPlayer)) {
            return;
        }
        cache.markDirty(event.entity.getUniqueID(), PlayerEquipmentCache.ALL);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        session.checkThread();
        if (event.phase != Phase.START) {
            return;
        }
        session.refresh();
        WorldClient world = session.world();
        if (world == null) {
            clearCache();
            return;
        }
        applyDirty(world);
    }

    private void onSessionChanged() {
        bindNetHandler(session.connection());
        bindWorld(session.world());
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

    private void bindWorld(WorldClient world) {
        if (boundWorld == world) {
            return;
        }
        unbindWorld();
        boolean hadEquipment = !cache.isEmpty();
        cache.clear();
        boundWorld = world;
        if (world instanceof PlayerEquipmentWorldBinding) {
            ((PlayerEquipmentWorldBinding) world).bindPlayerEquipmentObserver(entity -> {
                session.refresh();
                if (started && session.world() == world)
                    onEntityRemoved(entity);
            });
        }
        if (world != null) {
            for (EntityPlayer player : world.playerEntities) {
                cache.markDirty(player.getUniqueID(), PlayerEquipmentCache.ALL);
            }
        }
        if (hadEquipment)
            Listeners.notify(listeners, PlayerEquipmentListener::onPlayerEquipmentReset);
    }

    private void unbindWorld() {
        if (boundWorld instanceof PlayerEquipmentWorldBinding) {
            ((PlayerEquipmentWorldBinding) boundWorld).bindPlayerEquipmentObserver(null);
        }
        boundWorld = null;
    }

    private void applyDirty(WorldClient world) {
        List<Runnable> notifications = new ArrayList<>();
        for (Map.Entry<UUID, Integer> change : cache.drainDirty().entrySet()) {
            EntityPlayer player = world.getPlayerEntityByUUID(change.getKey());
            if (player == null) {
                if (cache.remove(change.getKey()) != null) {
                    notifications.add(() -> notifyRemoved(change.getKey()));
                }
                continue;
            }
            PlayerEquipmentSnapshot snapshot = cache.observe(player, change.getValue().intValue());
            notifications.add(() -> notifyChanged(snapshot, change.getValue().intValue()));
        }
        notifications.forEach(Runnable::run);
    }

    private void clearCache() {
        if (cache.isEmpty()) {
            cache.clear();
            return;
        }
        cache.clear();
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
        return equipmentSlot == 2 ? PlayerEquipmentCache.LEGGINGS : 0;
    }
}
