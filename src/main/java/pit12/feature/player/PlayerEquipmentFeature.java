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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import pit12.feature.Feature;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentCache;
import pit12.runtime.player.PlayerEquipmentListener;
import pit12.runtime.player.PlayerEquipmentPacketBinding;
import pit12.runtime.player.PlayerEquipmentPacketObserver;
import pit12.runtime.player.PlayerEquipmentSnapshot;
import pit12.runtime.player.PlayerEquipmentWorldBinding;
import pit12.runtime.player.PlayerEquipmentWorldObserver;

public final class PlayerEquipmentFeature implements Feature, PlayerEquipmentAccess,
        PlayerEquipmentPacketObserver, PlayerEquipmentWorldObserver {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final PlayerEquipmentCache cache = new PlayerEquipmentCache();
    private final List<PlayerEquipmentListener> listeners =
            new ArrayList<PlayerEquipmentListener>();
    private NetHandlerPlayClient boundNetHandler;
    private WorldClient boundWorld;
    private boolean started;

    @Override
    public void start() {
        if (started) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        started = true;
    }

    @Override
    public void stop() {
        if (!started) {
            clearCache();
            return;
        }
        started = false;
        unbindNetHandler();
        unbindWorld();
        MinecraftForge.EVENT_BUS.unregister(this);
        clearCache();
        listeners.clear();
    }

    @Override
    public PlayerEquipmentSnapshot loadedEquipment(UUID playerId) {
        return cache.loadedEquipment(playerId);
    }

    @Override
    public void addListener(PlayerEquipmentListener listener) {
        PlayerEquipmentListener nonNullListener = Objects.requireNonNull(listener, "listener");
        if (!listeners.contains(nonNullListener)) {
            listeners.add(nonNullListener);
        }
    }

    @Override
    public void removeListener(PlayerEquipmentListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onEquipmentPacket(int entityId, int equipmentSlot) {
        int slots = slotsFor(equipmentSlot);
        if (slots == 0) {
            return;
        }
        WorldClient world = minecraft.theWorld;
        Entity entity = world == null ? null : world.getEntityByID(entityId);
        if (entity instanceof EntityPlayer) {
            cache.markDirty(entity.getUniqueID(), slots);
        }
    }

    @Override
    public void onEntityRemoved(Entity entity) {
        if (entity instanceof EntityPlayer) {
            UUID playerId = entity.getUniqueID();
            if (cache.remove(playerId) != null) {
                notifyRemoved(playerId);
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!event.world.isRemote || !(event.entity instanceof EntityPlayer)) {
            return;
        }
        cache.markDirty(event.entity.getUniqueID(), PlayerEquipmentCache.ALL);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) {
            return;
        }
        WorldClient world = minecraft.theWorld;
        bindNetHandler(minecraft.getNetHandler());
        bindWorld(world);
        if (world == null) {
            clearCache();
            return;
        }
        applyDirty(world);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) {
            return;
        }
        clearCache();
        unbindWorld();
    }

    @SubscribeEvent
    public void onDisconnect(ClientDisconnectionFromServerEvent event) {
        clearCache();
        unbindNetHandler();
        unbindWorld();
    }

    private void bindNetHandler(NetHandlerPlayClient netHandler) {
        if (boundNetHandler == netHandler) {
            return;
        }
        unbindNetHandler();
        if (netHandler instanceof PlayerEquipmentPacketBinding) {
            ((PlayerEquipmentPacketBinding) netHandler).bindPlayerEquipmentObserver(this);
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
        clearCache();
        boundWorld = world;
        if (world instanceof PlayerEquipmentWorldBinding) {
            ((PlayerEquipmentWorldBinding) world).bindPlayerEquipmentObserver(this);
        }
        if (world != null) {
            for (EntityPlayer player : world.playerEntities) {
                cache.markDirty(player.getUniqueID(), PlayerEquipmentCache.ALL);
            }
        }
    }

    private void unbindWorld() {
        if (boundWorld instanceof PlayerEquipmentWorldBinding) {
            ((PlayerEquipmentWorldBinding) boundWorld).bindPlayerEquipmentObserver(null);
        }
        boundWorld = null;
    }

    private void applyDirty(WorldClient world) {
        for (Map.Entry<UUID, Integer> change : cache.drainDirty().entrySet()) {
            EntityPlayer player = world.getPlayerEntityByUUID(change.getKey());
            if (player == null) {
                if (cache.remove(change.getKey()) != null) {
                    notifyRemoved(change.getKey());
                }
                continue;
            }
            PlayerEquipmentSnapshot snapshot = cache.observe(player, change.getValue().intValue());
            notifyChanged(snapshot, change.getValue().intValue());
        }
    }

    private void clearCache() {
        if (cache.isEmpty()) {
            cache.clear();
            return;
        }
        cache.clear();
        for (PlayerEquipmentListener listener : new ArrayList<PlayerEquipmentListener>(listeners)) {
            listener.onPlayerEquipmentReset();
        }
    }

    private void notifyChanged(PlayerEquipmentSnapshot snapshot, int changedSlots) {
        for (PlayerEquipmentListener listener : new ArrayList<PlayerEquipmentListener>(listeners)) {
            listener.onPlayerEquipmentChanged(snapshot.playerId(), changedSlots,
                    snapshot.revision());
        }
    }

    private void notifyRemoved(UUID playerId) {
        for (PlayerEquipmentListener listener : new ArrayList<PlayerEquipmentListener>(listeners)) {
            listener.onPlayerEquipmentRemoved(playerId);
        }
    }

    private static int slotsFor(int equipmentSlot) {
        if (equipmentSlot == 0) {
            return PlayerEquipmentCache.HELD_ITEM;
        }
        return equipmentSlot == 2 ? PlayerEquipmentCache.LEGGINGS : 0;
    }
}
