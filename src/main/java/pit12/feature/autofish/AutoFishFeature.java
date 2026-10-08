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
package pit12.feature.autofish;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.session.ClientSession;
import pit12.shared.input.MinecraftActions;
import pit12.shared.lifecycle.ClientLifecycle;

public final class AutoFishFeature implements ClientLifecycle, ConfigChangeListener {
    private enum State {
        WAITING_FOR_RISE,
        WAITING_FOR_BITE,
        WAITING_FOR_RECOVERY,
        REEL_DELAY,
        EMPTY_REEL_DELAY,
        WAITING_FOR_REMOVAL,
        RECAST_DELAY
    }

    private static final int STABLE_TICKS = 10;
    private static final double STABLE_MOTION = 0.02D;
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ConfigCatalog configs;
    private final AutoFishConfig config;
    private final ClientSession session;
    private final MinecraftActions actions;
    private final Runnable sessionChanged = this::reset;
    private boolean started;
    private boolean active;
    private EntityFishHook hook;
    private State state = State.WAITING_FOR_RISE;
    private double floatingY;
    private int stableTicks;
    private boolean automaticClick;
    private PlayerInteractEvent clickEvent;
    private int delayTicks;
    private boolean emptyHook;

    public AutoFishFeature(ConfigCatalog configs, AutoFishConfig config, ClientSession session,
            MinecraftActions actions) {
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.actions = actions;
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
        if (!started) {
            return;
        }
        started = false;
        configs.removeListener(this);
        updateActivation();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (changes.affects("autofish", "enabled")) {
            updateActivation();
        }
    }

    private void updateActivation() {
        boolean enabled = started && config.enabled();
        if (enabled == active) {
            return;
        }
        active = enabled;
        if (enabled) {
            session.addListener(sessionChanged);
            MinecraftForge.EVENT_BUS.register(this);
        } else {
            MinecraftForge.EVENT_BUS.unregister(this);
            session.removeListener(sessionChanged);
        }
        reset();
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (!active || event.phase != Phase.START) {
            return;
        }
        EntityPlayerSP player = minecraft.thePlayer;
        GuiScreen screen = minecraft.currentScreen;
        if (player == null || minecraft.theWorld == null
                || (screen != null && !(screen instanceof GuiChat)
                        && !(screen instanceof GuiIngameMenu))
                || !player.isEntityAlive() || player.isSpectator()) {
            reset();
            return;
        }
        ItemStack rod = player.getHeldItem();
        if (rod == null || rod.stackSize <= 0 || !(rod.getItem() instanceof ItemFishingRod)) {
            reset();
            return;
        }
        // Minecraft clears this reference when the hook dies.
        EntityFishHook current = player.fishEntity;
        if (current != hook) {
            boolean recast = state == State.WAITING_FOR_REMOVAL && current == null
                    && config.autoRecast.get();
            reset();
            hook = current;
            if (hook != null) {
                emptyHook = chance(config.emptyHookChance.get());
            } else if (recast) {
                state = State.RECAST_DELAY;
                delayTicks = delay(config.castDelayMin, config.castDelayMax);
            }
        }
        if (hook == null) {
            if (state != State.RECAST_DELAY) {
                return;
            }
            if (!config.autoRecast.get()) {
                reset();
                return;
            }
            if (delayTicks > 0) {
                delayTicks--;
                return;
            }
            // Reset first to prevent retries if the cast is canceled or the hook spawns later.
            reset();
            rightClick();
            return;
        }
        if (state == State.WAITING_FOR_REMOVAL) {
            return;
        }
        if (hook.isDead || hook.caughtEntity != null
                || (state != State.REEL_DELAY && !hook.isInWater())) {
            state = State.WAITING_FOR_RISE;
            stableTicks = 0;
            return;
        }
        double dy = hook.posY - hook.prevPosY;
        switch (state) {
            case WAITING_FOR_RISE:
                if (dy > 0.0D) {
                    floatingY = hook.posY;
                    state = State.WAITING_FOR_BITE;
                }
                return;
            case WAITING_FOR_BITE:
                if (dy > 0.0D) {
                    floatingY = hook.posY;
                }
                if (dy >= -0.05D) {
                    return;
                }
                stableTicks = 0;
                if (chance(config.missHookChance.get())) {
                    state = State.WAITING_FOR_RECOVERY;
                    return;
                }
                delayTicks = delay(config.reelDelayMin, config.reelDelayMax);
                if (emptyHook) {
                    state = State.EMPTY_REEL_DELAY;
                    return;
                }
                state = State.REEL_DELAY;
                break;
            case WAITING_FOR_RECOVERY:
            case EMPTY_REEL_DELAY:
                // A hook can pause below the surface during a bite. Wait for it to rise too.
                stableTicks =
                        floatingY - hook.posY <= STABLE_MOTION && Math.abs(dy) <= STABLE_MOTION
                                ? stableTicks + 1
                                : 0;
                if (stableTicks < STABLE_TICKS) {
                    return;
                }
                if (state == State.WAITING_FOR_RECOVERY) {
                    state = State.WAITING_FOR_RISE;
                    return;
                }
                break;
            case REEL_DELAY:
                break;
            default:
                return;
        }
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }
        stableTicks = 0;
        state = rightClick() ? State.WAITING_FOR_REMOVAL : State.WAITING_FOR_RECOVERY;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.entityPlayer != minecraft.thePlayer
                || event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (!automaticClick) {
            reset();
        } else if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
            clickEvent = event;
        }
    }

    private static int delay(IntegerSetting minimum, IntegerSetting maximum) {
        int first = minimum.get();
        int second = maximum.get();
        return ThreadLocalRandom.current().nextInt(Math.min(first, second),
                Math.max(first, second) + 1);
    }

    private static boolean chance(int percent) {
        return ThreadLocalRandom.current().nextInt(100) < percent;
    }

    private boolean rightClick() {
        automaticClick = true;
        try {
            actions.pit12$rightClick();
            // Read after dispatch so later listeners can still cancel the click.
            return clickEvent != null && !clickEvent.isCanceled();
        } finally {
            automaticClick = false;
            clickEvent = null;
        }
    }

    private void reset() {
        hook = null;
        state = State.WAITING_FOR_RISE;
        floatingY = 0.0;
        stableTicks = 0;
        delayTicks = 0;
        emptyHook = false;
    }
}
