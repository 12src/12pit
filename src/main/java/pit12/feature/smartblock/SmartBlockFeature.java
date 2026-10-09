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
package pit12.feature.smartblock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import org.lwjgl.opengl.Display;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.Setting;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.session.ClientSession;
import pit12.shared.lifecycle.ClientLifecycle;

public final class SmartBlockFeature implements ClientLifecycle, ConfigChangeListener {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final SmartBlockConfig config;
    private final ClientSession session;
    private final SmartBlockBinding binding;
    private final Runnable sessionChanged = this::reset;
    private boolean started;
    private boolean active;
    private EntityPlayerSP player;
    private int originalSlot = -1;
    private int blockingSlot = -1;
    private int bulletTimeSlot = -1;
    private int selectedSlot = -1;
    private int pendingSlot = -1;
    private int delayTicks;
    private boolean searched;
    private boolean interrupted;

    public SmartBlockFeature(Minecraft minecraft, ConfigCatalog configs, SmartBlockConfig config,
            ClientSession session, SmartBlockBinding binding) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.binding = binding;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started) {
            return;
        }
        started = true;
        configs.addListener(this);
        updateActivation();
    }

    @Override
    public void stop() {
        session.checkThread();
        started = false;
        configs.removeListener(this);
        updateActivation();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        for (Setting<?> setting : config.settings()) {
            if (changes.affects(config.id(), setting.id())) {
                cancel();
                updateActivation();
                return;
            }
        }
    }

    private void updateActivation() {
        boolean enabled =
                started && config.enabled() && (config.bulletTime.get() || config.bruiser.get());
        if (enabled == active) {
            return;
        }
        active = enabled;
        if (enabled) {
            session.addListener(sessionChanged);
            binding.pit12$bindSmartBlock(this);
        } else {
            binding.pit12$bindSmartBlock(null);
            session.removeListener(sessionChanged);
            cancel();
        }
    }

    public void inputTick() {
        if (player != minecraft.thePlayer) {
            reset();
            player = minecraft.thePlayer;
        }
        if (player == null || minecraft.theWorld == null || minecraft.currentScreen != null
                || !minecraft.inGameHasFocus || !Display.isActive() || !player.isEntityAlive()
                || player.isSpectator()) {
            cancel();
            return;
        }
        boolean using = minecraft.gameSettings.keyBindUseItem.isKeyDown();
        if (!using) {
            interrupted = false;
            searched = false;
        }
        if (interrupted) {
            return;
        }
        if (originalSlot >= 0 && player.inventory.currentItem != selectedSlot) {
            cancel();
            return;
        }
        if (using && !searched) {
            if (!player.isBlocking() && !(config.emptyHand.get() && player.getHeldItem() == null)) {
                return;
            }
            searched = true;
            findSwords();
            if (blockingSlot < 0) {
                if (originalSlot >= 0) {
                    cancel();
                }
                return;
            }
            if (originalSlot < 0) {
                originalSlot = player.inventory.currentItem;
                selectedSlot = originalSlot;
            }
        }
        if (originalSlot < 0) {
            return;
        }
        if (using && config.bulletTimeNearArrows.get() && bulletTimeSlot >= 0
                && bulletTimeSlot != blockingSlot && isSword(bulletTimeSlot) && hasMovingArrow()) {
            blockingSlot = bulletTimeSlot;
        }
        if ((player.inventory.getStackInSlot(originalSlot) != null && !isSword(originalSlot))
                || (using && !isSword(blockingSlot))) {
            cancel();
            return;
        }
        int target = using ? blockingSlot : originalSlot;
        if (target == selectedSlot) {
            pendingSlot = -1;
            if (!using) {
                reset();
            }
            return;
        }
        if (pendingSlot != target) {
            pendingSlot = target;
            delayTicks = using ? config.switchDelay.get() : config.restoreDelay.get();
        }
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }
        select(target);
        pendingSlot = -1;
        if (!using) {
            reset();
        }
    }

    public boolean slotLocked() {
        return config.lockSlot.get() && originalSlot >= 0 && player == minecraft.thePlayer;
    }

    public void cancel() {
        if (player != null && player == minecraft.thePlayer && originalSlot >= 0
                && player.inventory.currentItem == selectedSlot
                && (player.inventory.getStackInSlot(originalSlot) == null
                        || isSword(originalSlot))) {
            select(originalSlot);
        }
        reset();
        player = active ? minecraft.thePlayer : null;
        interrupted = minecraft.gameSettings.keyBindUseItem.isKeyDown();
    }

    private void findSwords() {
        blockingSlot = -1;
        bulletTimeSlot = -1;
        int bestPreferredLevel = -1;
        int bestOtherLevel = -1;
        int bestBulletTimeLevel = 0;
        int bestBulletTimeBruiserLevel = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (!isSword(slot)) {
                continue;
            }
            ItemStack sword = player.inventory.getStackInSlot(slot);
            int bulletTime = config.bulletTime.get()
                    ? PitEnchantmentReader.levelOf(sword, PitEnchantment.Bullet_Time)
                    : 0;
            int bruiser = config.bruiser.get()
                    ? PitEnchantmentReader.levelOf(sword, PitEnchantment.Bruiser)
                    : 0;
            if (bulletTime == 0 && bruiser == 0) {
                continue;
            }
            int preferredLevel = config.priority.get() == 0 ? bulletTime : bruiser;
            int otherLevel = config.priority.get() == 0 ? bruiser : bulletTime;
            if (preferredLevel > bestPreferredLevel || (preferredLevel == bestPreferredLevel
                    && (otherLevel > bestOtherLevel || (otherLevel == bestOtherLevel
                            && slot == player.inventory.currentItem)))) {
                blockingSlot = slot;
                bestPreferredLevel = preferredLevel;
                bestOtherLevel = otherLevel;
            }
            if (bulletTime > 0 && (bulletTime > bestBulletTimeLevel
                    || (bulletTime == bestBulletTimeLevel && (bruiser > bestBulletTimeBruiserLevel
                            || (bruiser == bestBulletTimeBruiserLevel
                                    && slot == player.inventory.currentItem))))) {
                bulletTimeSlot = slot;
                bestBulletTimeLevel = bulletTime;
                bestBulletTimeBruiserLevel = bruiser;
            }
        }
    }

    private boolean hasMovingArrow() {
        double range = config.arrowRange.get();
        for (EntityArrow arrow : minecraft.theWorld.getEntitiesWithinAABB(EntityArrow.class,
                player.getEntityBoundingBox().expand(range, range, range))) {
            if (!arrow.isDead && !((ArrowAccess) arrow).pit12$isInGround()
                    && player.getDistanceSqToEntity(arrow) <= range * range
                    && (arrow.motionX != 0.0 || arrow.motionY != 0.0 || arrow.motionZ != 0.0)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSword(int slot) {
        ItemStack stack = player.inventory.getStackInSlot(slot);
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemSword;
    }

    private void select(int slot) {
        if (player.inventory.currentItem == slot) {
            return;
        }
        // Keep item use intact so vanilla decides whether to discard this tick's clicks.
        player.inventory.currentItem = slot;
        selectedSlot = slot;
    }

    private void reset() {
        player = null;
        originalSlot = -1;
        blockingSlot = -1;
        bulletTimeSlot = -1;
        selectedSlot = -1;
        pendingSlot = -1;
        delayTicks = 0;
        searched = false;
        interrupted = false;
    }
}
