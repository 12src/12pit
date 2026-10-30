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
package pit12.feature.timers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentCache;
import pit12.runtime.player.PlayerEquipmentListener;
import pit12.runtime.player.PlayerEquipmentSnapshot;
import pit12.runtime.session.ClientSession;

public final class VenomTracker implements PlayerEquipmentListener {
    private final Minecraft minecraft;
    private final TimersConfig config;
    private final ClientSession session;
    private final PlayerEntityTracker players;
    private final PlayerEquipmentAccess equipment;
    private final BiConsumer<EntityPlayer, EntityPlayer> triggered;
    private final Runnable resetTimers;
    private final Runnable sessionChanged = this::onSessionChanged;
    private final Map<UUID, Combo> wearers = new HashMap<>();
    private NetHandlerPlayClient boundConnection;

    VenomTracker(Minecraft minecraft, TimersConfig config, ClientSession session,
            PlayerEntityTracker players, PlayerEquipmentAccess equipment,
            BiConsumer<EntityPlayer, EntityPlayer> triggered, Runnable resetTimers) {
        this.minecraft = minecraft;
        this.config = config;
        this.session = session;
        this.players = players;
        this.equipment = equipment;
        this.triggered = triggered;
        this.resetTimers = resetTimers;
    }

    void active(boolean active) {
        if (active) {
            equipment.addListener(this);
            session.addListener(sessionChanged);
            onSessionChanged();
        } else {
            equipment.removeListener(this);
            session.removeListener(sessionChanged);
            unbind();
            wearers.clear();
            resetTimers.run();
        }
    }

    private void onSessionChanged() {
        unbind();
        wearers.clear();
        resetTimers.run();
        NetHandlerPlayClient connection = session.connection();
        if (connection instanceof VenomPacketBinding) {
            ((VenomPacketBinding) connection).bindVenomTracker(this);
            boundConnection = connection;
        }
        if (session.world() != null) {
            for (EntityPlayer player : session.world().playerEntities) {
                observe(player, PitEnchantmentReader.contains(player.getCurrentArmor(1),
                        PitEnchantment.Combo_Venom));
            }
        }
    }

    private void unbind() {
        if (boundConnection instanceof VenomPacketBinding)
            ((VenomPacketBinding) boundConnection).bindVenomTracker(null);
        boundConnection = null;
    }

    @Override
    public void onPlayerEquipmentChanged(UUID playerId, int changedSlots, long revision) {
        if ((changedSlots & PlayerEquipmentCache.LEGGINGS) == 0)
            return;
        EntityPlayer player = players.loadedPlayer(playerId);
        PlayerEquipmentSnapshot snapshot = equipment.loadedEquipment(playerId);
        if (player != null && snapshot != null)
            observe(player, snapshot.leggingsEnchantments().contains(PitEnchantment.Combo_Venom));
    }

    @Override
    public void onPlayerEquipmentRemoved(UUID playerId) {
        wearers.remove(playerId);
    }

    @Override
    public void onPlayerEquipmentReset() {
        wearers.clear();
    }

    private void observe(EntityPlayer player, boolean venom) {
        UUID playerId = player.getUniqueID();
        if (!venom || !player.isEntityAlive()) {
            wearers.remove(playerId);
            return;
        }
        Combo previous = wearers.get(playerId);
        if (previous == null || previous.player != player)
            wearers.put(playerId, new Combo(player));
    }

    private void observeLocal() {
        EntityPlayer player = minecraft.thePlayer;
        if (player != null)
            observe(player, PitEnchantmentReader.contains(player.getCurrentArmor(1),
                    PitEnchantment.Combo_Venom));
    }

    public boolean trackingHits() {
        return !wearers.isEmpty();
    }

    public void rotation(EntityPlayer player, byte yaw, byte pitch) {
        Combo combo = wearers.get(player.getUniqueID());
        if (combo != null) {
            combo.yaw = yaw * 360.0F / 256.0F;
            combo.pitch = pitch * 360.0F / 256.0F;
        }
    }

    public void swing(EntityPlayer player) {
        if (player == minecraft.thePlayer)
            return;
        Combo combo = wearers.get(player.getUniqueID());
        if (combo == null)
            return;
        // Use packet coordinates and rotation before display interpolation.
        Vec3 from = new Vec3(player.serverPosX / 32.0,
                player.serverPosY / 32.0 + player.getEyeHeight(), player.serverPosZ / 32.0);
        float yaw = combo.yaw * (float) Math.PI / 180;
        float pitch = combo.pitch * (float) Math.PI / 180;
        double reach = config.venomReach.get();
        double horizontal = MathHelper.cos(pitch) * reach;
        Vec3 to = from.addVector(-MathHelper.sin(yaw) * horizontal, -MathHelper.sin(pitch) * reach,
                MathHelper.cos(yaw) * horizontal);
        combo.swungAt = System.nanoTime();
        combo.from = from;
        combo.to = to;
    }

    void attack(EntityPlayer target) {
        observeLocal();
        if (minecraft.thePlayer == null)
            return;
        Combo combo = wearers.get(minecraft.thePlayer.getUniqueID());
        if (combo != null) {
            combo.swungAt = System.nanoTime();
            combo.localTarget = target;
            combo.from = minecraft.thePlayer.getPositionEyes(1);
        }
    }

    public void hurt(EntityPlayer player) {
        double margin = config.venomHitboxMargin.get();
        // This coarse check also allows for eye height and the hitbox margin.
        double range = config.venomReach.get() + 2 + margin;
        double rangeSq = range * range;
        long now = System.nanoTime();
        long window = config.venomHitWindow.get() * 1_000_000L;
        Combo attacker = null;
        AxisAlignedBB displayBox = null;
        AxisAlignedBB serverBox = null;
        for (Combo combo : wearers.values()) {
            if (combo.player == player || combo.swungAt == 0 || now - combo.swungAt > window
                    || combo.player.getDistanceSqToEntity(player) > rangeSq)
                continue;
            if (combo.localTarget != null && combo.localTarget != player)
                continue;
            if (displayBox == null) {
                displayBox = player.getEntityBoundingBox().expand(margin, margin, margin);
                serverBox = player == minecraft.thePlayer ? displayBox
                        : displayBox.offset(player.serverPosX / 32.0 - player.posX,
                                player.serverPosY / 32.0 - player.posY,
                                player.serverPosZ / 32.0 - player.posZ);
            }
            if (combo.localTarget != null) {
                double x = Math.max(0, Math.max(displayBox.minX - combo.from.xCoord,
                        combo.from.xCoord - displayBox.maxX));
                double y = Math.max(0, Math.max(displayBox.minY - combo.from.yCoord,
                        combo.from.yCoord - displayBox.maxY));
                double z = Math.max(0, Math.max(displayBox.minZ - combo.from.zCoord,
                        combo.from.zCoord - displayBox.maxZ));
                double reach = config.venomReach.get();
                if (x * x + y * y + z * z > reach * reach)
                    continue;
            } else if (misses(combo, serverBox)
                    && (serverBox == displayBox || misses(combo, displayBox))) {
                continue;
            }
            // Hurt packets do not name the attacker. Leave ambiguous hits uncounted.
            if (attacker != null)
                return;
            attacker = combo;
        }
        if (attacker == null)
            return;
        attacker.swungAt = 0;
        attacker.localTarget = null;
        if (attacker.target != player) {
            attacker.target = player;
            attacker.hits = 0;
        }
        if (++attacker.hits == 3) {
            attacker.hits = 0;
            triggered.accept(attacker.player, player);
        }
    }

    void tick() {
        observeLocal();
        if (wearers.isEmpty())
            return;
        wearers.values().removeIf(combo -> !combo.player.isEntityAlive()
                || players.loadedPlayer(combo.player.getUniqueID()) != combo.player);
    }

    private static boolean misses(Combo combo, AxisAlignedBB box) {
        if (Math.max(combo.from.xCoord, combo.to.xCoord) < box.minX
                || Math.min(combo.from.xCoord, combo.to.xCoord) > box.maxX
                || Math.max(combo.from.yCoord, combo.to.yCoord) < box.minY
                || Math.min(combo.from.yCoord, combo.to.yCoord) > box.maxY
                || Math.max(combo.from.zCoord, combo.to.zCoord) < box.minZ
                || Math.min(combo.from.zCoord, combo.to.zCoord) > box.maxZ)
            return true;
        return !box.isVecInside(combo.from) && box.calculateIntercept(combo.from, combo.to) == null;
    }

    private static final class Combo {
        final EntityPlayer player;
        EntityPlayer target;
        int hits;
        float yaw;
        float pitch;
        long swungAt;
        EntityPlayer localTarget;
        Vec3 from;
        Vec3 to;

        Combo(EntityPlayer player) {
            this.player = player;
            yaw = player.rotationYaw;
            pitch = player.rotationPitch;
        }
    }
}
