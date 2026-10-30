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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentCache;
import pit12.runtime.player.PlayerEquipmentListener;
import pit12.runtime.session.ClientSession;
import pit12.shared.entity.PlayerPositions;

public final class JumpBoostTracker implements PlayerEquipmentListener {
    private static final long DURATION_NANOS = 30_000_000_000L;
    private final Minecraft minecraft;
    private final TimersConfig config;
    private final ClientSession session;
    private final PlayerEntityTracker players;
    private final PlayerEquipmentAccess equipment;
    private final BiConsumer<EntityPlayer, Long> triggered;
    private final Consumer<EntityPlayer> removed;
    private final Runnable resetTimers;
    private final Runnable sessionChanged = this::onSessionChanged;
    private final Map<UUID, HeldPotion> heldPotions = new HashMap<>();
    private final Map<UUID, Jump> jumps = new HashMap<>();
    private NetHandlerPlayClient boundConnection;

    JumpBoostTracker(Minecraft minecraft, TimersConfig config, ClientSession session,
            PlayerEntityTracker players, PlayerEquipmentAccess equipment,
            BiConsumer<EntityPlayer, Long> triggered, Consumer<EntityPlayer> removed,
            Runnable resetTimers) {
        this.minecraft = minecraft;
        this.config = config;
        this.session = session;
        this.players = players;
        this.equipment = equipment;
        this.triggered = triggered;
        this.removed = removed;
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
            onPlayerEquipmentReset();
        }
    }

    private void onSessionChanged() {
        unbind();
        onPlayerEquipmentReset();
        NetHandlerPlayClient connection = session.connection();
        if (connection instanceof JumpBoostPacketBinding) {
            ((JumpBoostPacketBinding) connection).bindJumpBoostTracker(this);
            boundConnection = connection;
        }
        if (session.world() != null) {
            for (EntityPlayer player : session.world().playerEntities)
                observe(player);
        }
    }

    private void unbind() {
        if (boundConnection instanceof JumpBoostPacketBinding)
            ((JumpBoostPacketBinding) boundConnection).bindJumpBoostTracker(null);
        boundConnection = null;
    }

    @Override
    public void onPlayerEquipmentReceived(UUID playerId, ItemStack stack) {
        // This callback also receives armor packets, so read the held slot directly.
        EntityPlayer player = players.loadedPlayer(playerId);
        if (player != null)
            observe(player);
    }

    @Override
    public void onPlayerEquipmentChanged(UUID playerId, int changedSlots, long revision) {
        if ((changedSlots & PlayerEquipmentCache.HELD_ITEM) == 0)
            return;
        EntityPlayer player = players.loadedPlayer(playerId);
        if (player != null)
            observe(player);
    }

    @Override
    public void onPlayerEquipmentRemoved(UUID playerId) {
        heldPotions.remove(playerId);
        jumps.remove(playerId);
    }

    @Override
    public void onPlayerEquipmentReset() {
        heldPotions.clear();
        resetJumpChecks();
        resetTimers.run();
    }

    void tick() {
        if (minecraft.thePlayer != null) {
            observe(minecraft.thePlayer);
            observeJump(minecraft.thePlayer, false);
        }
        heldPotions.values().removeIf(potion -> !potion.player.isEntityAlive());
        if (!jumps.isEmpty()) {
            long now = System.nanoTime();
            jumps.values().removeIf(jump -> now >= jump.until || !jump.player.isEntityAlive());
        }
    }

    void trackJumps(EntityPlayer player, long until) {
        if (config.jumpBoostResetOnLowJump.get())
            jumps.put(player.getUniqueID(), new Jump(player, until, player == minecraft.thePlayer));
    }

    void resetJumpChecks() {
        jumps.clear();
    }

    public boolean checkingJumps() {
        return !jumps.isEmpty();
    }

    public void observeJump(EntityPlayer player, boolean interrupted) {
        if (jumps.isEmpty())
            return;
        UUID playerId = player.getUniqueID();
        Jump jump = jumps.get(playerId);
        if (jump == null || jump.player != player)
            return;
        boolean local = player == minecraft.thePlayer;
        // Remote positions must come from packets, before movement interpolation.
        double x = local ? player.posX : player.serverPosX / 32.0;
        double y = local ? player.posY : player.serverPosY / 32.0;
        double z = local ? player.posZ : player.serverPosZ / 32.0;
        double dx = x - jump.x;
        double dy = y - jump.y;
        double dz = z - jump.z;
        if (interrupted || dx * dx + dy * dy + dz * dz > 16 || player.hurtTime > 0
                || player.isRiding() || player.capabilities.isFlying || player.isInWater()
                || player.isInLava() || player.isOnLadder()) {
            jump.jumping = false;
        } else {
            if (jump.grounded && !player.onGround && y > jump.y + 0.1) {
                jump.startY = jump.y;
                // Give the potion effect time to reach the jumping player.
                jump.jumping = System.nanoTime() >= jump.until - DURATION_NANOS
                        + config.jumpBoostGrace.get() * 1_000_000L;
            }
            // Jump Boost IV peaks near 4.15 blocks. Packets can miss the exact peak.
            if (jump.jumping && y - jump.startY >= 3.9)
                jump.jumping = false;
            if (jump.jumping) {
                double halfWidth = player.width / 2.0;
                AxisAlignedBB headroom = new AxisAlignedBB(Math.min(jump.x, x) - halfWidth,
                        Math.min(jump.y, y) + player.height - 0.0625,
                        Math.min(jump.z, z) - halfWidth, Math.max(jump.x, x) + halfWidth,
                        jump.startY + 4.15 + player.height, Math.max(jump.z, z) + halfWidth);
                if (!player.worldObj.getCollidingBoundingBoxes(player, headroom).isEmpty()) {
                    jump.jumping = false;
                } else if (player.onGround || y < jump.y) {
                    jumps.remove(playerId);
                    removed.accept(player);
                    return;
                }
            }
        }
        jump.x = x;
        jump.y = y;
        jump.z = z;
        jump.grounded = player.onGround;
    }

    private void observe(EntityPlayer player) {
        UUID playerId = player.getUniqueID();
        if (!player.isEntityAlive()) {
            heldPotions.remove(playerId);
            return;
        }
        ItemStack stack = player.getHeldItem();
        HeldPotion previous = heldPotions.get(playerId);
        if (previous != null && previous.player == player
                && ItemStack.areItemStacksEqual(previous.stack, stack))
            return;
        long now = System.nanoTime();
        if (previous != null) {
            heldPotions.remove(playerId);
            if (previous.player == player && stack != null && stack.getItem() == Items.glass_bottle
                    && (config.jumpBoostSoundRequired.get()
                            ? previous.lastDrinkSound != 0
                                    && now - previous.lastDrinkSound <= 500_000_000L
                            : now - previous.heldSince >= previous.stack.getMaxItemUseDuration()
                                    * 50_000_000L))
                triggered.accept(player, now + DURATION_NANOS);
        }
        if (stack == null || stack.getItem() != Items.potionitem
                || ItemPotion.isSplash(stack.getMetadata()))
            return;
        List<PotionEffect> effects = Items.potionitem.getEffects(stack);
        if (effects == null)
            return;
        for (PotionEffect effect : effects) {
            if (effect.getPotionID() == Potion.jump.id && effect.getAmplifier() == 3) {
                heldPotions.put(playerId, new HeldPotion(player, stack, now));
                return;
            }
        }
    }

    void drink(EntityPlayer player) {
        observe(player);
        HeldPotion potion = heldPotions.get(player.getUniqueID());
        if (potion != null)
            potion.lastDrinkSound = System.nanoTime();
    }

    public void sound(double x, double y, double z) {
        if (!config.jumpBoostSoundRequired.get() || heldPotions.isEmpty())
            return;
        // Sound packets have a position but no player ID.
        HeldPotion closest = null;
        double closestDistance = 4;
        for (HeldPotion potion : heldPotions.values()) {
            EntityPlayer player = potion.player;
            if (!player.isEntityAlive())
                continue;
            double distance =
                    PlayerPositions.serverDistanceSquared(player, minecraft.thePlayer, x, y, z);
            if (distance < closestDistance) {
                closest = potion;
                closestDistance = distance;
            } else if (distance == closestDistance) {
                closest = null;
            }
        }
        if (closest != null)
            closest.lastDrinkSound = System.nanoTime();
    }

    private static final class Jump {
        final EntityPlayer player;
        final long until;
        double x;
        double y;
        double z;
        double startY;
        boolean grounded;
        boolean jumping;

        Jump(EntityPlayer player, long until, boolean local) {
            this.player = player;
            this.until = until;
            x = local ? player.posX : player.serverPosX / 32.0;
            y = local ? player.posY : player.serverPosY / 32.0;
            z = local ? player.posZ : player.serverPosZ / 32.0;
            grounded = player.onGround;
        }
    }
    private static final class HeldPotion {
        final EntityPlayer player;
        final ItemStack stack;
        final long heldSince;
        long lastDrinkSound;

        HeldPotion(EntityPlayer player, ItemStack stack, long heldSince) {
            this.player = player;
            this.stack = stack.copy();
            this.heldSince = heldSince;
        }
    }
}
