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
package pit12.feature.combat.enhancedtelebow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.provider.telebow.api.TelebowPrediction;
import pit12.runtime.arrow.ArrowAccess;
import pit12.runtime.arrow.ArrowListener;
import pit12.runtime.arrow.ArrowTracker;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.shared.lifecycle.ClientLifecycle;

public final class EnhancedTelebowFeature
        implements ClientLifecycle, ConfigChangeListener, ArrowListener {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final EnhancedTelebowConfig config;
    private final EnhancedTelebowBinding binding;
    private final ArrowTracker arrows;
    private final TelebowRelease release;
    private final TelebowPrediction prediction;
    private boolean active;
    private boolean keepAim;
    private long tick;
    private Landing landing;
    private Teleport teleport;

    public EnhancedTelebowFeature(Minecraft minecraft, ConfigCatalog configs,
            EnhancedTelebowConfig config, EnhancedTelebowBinding binding, ArrowTracker arrows,
            TelebowPrediction prediction) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.binding = binding;
        this.arrows = arrows;
        this.prediction = prediction;
        release = new TelebowRelease(minecraft);
    }

    @Override
    public void start() {
        configs.addListener(this);
        updateOptions(config.enabled());
    }

    @Override
    public void stop() {
        configs.removeListener(this);
        updateOptions(false);
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (changes.affects(config.id(), "enabled")
                || changes.affects(config.id(), config.keepAim.id())
                || changes.affects(config.id(), config.sneakOnRelease.id()))
            updateOptions(config.enabled());
    }

    private void updateOptions(boolean enabled) {
        boolean track = enabled && config.keepAim.get();
        if (track != keepAim) {
            keepAim = track;
            if (track) {
                arrows.addListener(this);
            } else {
                arrows.removeListener(this);
                onArrowsReset();
            }
        }
        boolean assist = enabled && config.sneakOnRelease.get();
        if (!assist)
            release.reset();
        if (active == (track || assist))
            return;
        active = track || assist;
        if (active) {
            MinecraftForge.EVENT_BUS.register(this);
            binding.pit12$bindEnhancedTelebow(this);
        } else {
            binding.pit12$bindEnhancedTelebow(null);
            MinecraftForge.EVENT_BUS.unregister(this);
        }
    }

    public TelebowRelease release() {
        return config.sneakOnRelease.get() ? release : null;
    }

    @Override
    public boolean onArrowSpawn(EntityArrow arrow, EntityPlayer shooter, ItemStack bow,
            boolean sneaking, long lastSneakingNanos) {
        return shooter == minecraft.thePlayer
                && (sneaking || lastSneakingNanos != 0
                        && System.nanoTime() - lastSneakingNanos <= prediction.hitWindowNanos(true))
                && PitEnchantmentReader.contains(bow, PitEnchantment.Telebow);
    }

    @Override
    public void onArrowRemoved(EntityArrow arrow, boolean serverRemoved) {
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || arrow.shootingEntity != player
                || !(serverRemoved || arrow.isDead || ((ArrowAccess) arrow).pit12$isInGround()))
            return;
        landing = new Landing(player, arrow.getPositionVector(), System.nanoTime(), tick);
        restoreLook();
    }

    public void beforePositionLook(EntityPlayerSP player) {
        teleport = !keepAim || player == null ? null : new Teleport(player);
    }

    public void afterPositionLook() {
        Teleport current = teleport;
        if (current == null)
            return;
        EntityPlayerSP player = current.player;
        if (player.posX == current.x && player.posY == current.y && player.posZ == current.z) {
            teleport = null;
            return;
        }
        current.destination = player.getPositionVector();
        current.serverYaw = player.rotationYaw;
        current.serverPitch = player.rotationPitch;
        current.at = System.nanoTime();
        current.tick = tick;
        restoreLook();
    }

    private void restoreLook() {
        Teleport current = teleport;
        if (landing == null || current == null || current.destination == null
                || landing.player != current.player || current.player != minecraft.thePlayer
                || !prediction.matchesTeleport(landing.position, current.destination,
                        landing.at - current.at, landing.tick == current.tick, true))
            return;
        onArrowsReset();
        EntityPlayerSP player = current.player;
        if (player.rotationYaw != current.serverYaw || player.rotationPitch != current.serverPitch)
            return;
        // The teleport reply bypasses the walking update's look cache.
        ((TelebowPlayer) player).pit12$recordTeleportLook(current.serverYaw, current.serverPitch);
        player.rotationYaw = current.yaw;
        player.rotationPitch = current.pitch;
        player.prevRotationYaw = current.prevYaw;
        player.prevRotationPitch = current.prevPitch;
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase == Phase.END) {
            tick++;
            release.clearOutsideGameplay();
        }
    }

    @Override
    public void onArrowsReset() {
        landing = null;
        teleport = null;
    }

    private static final class Landing {
        final EntityPlayerSP player;
        final Vec3 position;
        final long at;
        final long tick;

        Landing(EntityPlayerSP player, Vec3 position, long at, long tick) {
            this.player = player;
            this.position = position;
            this.at = at;
            this.tick = tick;
        }
    }
    private static final class Teleport {
        final EntityPlayerSP player;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;
        final float prevYaw;
        final float prevPitch;
        Vec3 destination;
        float serverYaw;
        float serverPitch;
        long at;
        long tick;

        Teleport(EntityPlayerSP player) {
            this.player = player;
            x = player.posX;
            y = player.posY;
            z = player.posZ;
            yaw = player.rotationYaw;
            pitch = player.rotationPitch;
            prevYaw = player.prevRotationYaw;
            prevPitch = player.prevRotationPitch;
        }
    }
}
