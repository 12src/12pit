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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationLookup;
import pit12.feature.telebow.api.TelebowCooldownListener;
import pit12.feature.telebow.api.TelebowCooldowns;
import pit12.feature.timers.TimersHud.Group;
import pit12.feature.timers.TimersHud.TimerType;
import pit12.feature.timers.api.PlayerTimers;
import pit12.runtime.arrow.ArrowTracker;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.hud.HudBounds;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.hud.HudRenderer;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerNameCache;
import pit12.runtime.session.ClientSession;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.ClientEvents.Attack;
import pit12.shared.event.ClientEvents.Overlay;
import pit12.shared.event.ClientEvents.Sound;
import pit12.shared.lifecycle.ClientLifecycle;

public final class TimersFeature
        implements ClientLifecycle, ConfigChangeListener, TelebowCooldownListener, PlayerTimers {
    private final Consumer<Attack> onAttackListener = this::onAttack;
    private final Consumer<Sound> onSoundListener = this::onSound;
    private final Runnable onTickListener = this::onTick;
    private final Consumer<Overlay> onRenderOverlayListener = this::onRenderOverlay;

    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final TimersConfig config;
    private final ClientSession session;
    private final RelationLookup relations;
    private final PlayerNameCache playerNames;
    private final HudRegistry hudRegistry;
    private final TelebowCooldowns telebow;
    private final HudRenderer renderer = new HudRenderer();
    private final TimersHud hud;
    private final PinTracker pin;
    private final VenomTracker venom;
    private final JumpBoostTracker jumpBoost;
    private final Map<UUID, Timer> pinTimers = new LinkedHashMap<>();
    private final Map<UUID, Timer> venomTimers = new LinkedHashMap<>();
    private final Map<UUID, Timer> jumpBoostTimers = new LinkedHashMap<>();
    private final Map<UUID, Timer> telebowCooldowns = new LinkedHashMap<>();
    private final Map<UUID, String> countdownTexts = new HashMap<>();
    private List<TimersHud.Row> rows = new ArrayList<>();
    private List<TimersHud.Row> pendingRows = new ArrayList<>();
    private long nextRefresh;
    private int countdownTypes;
    private boolean started;
    private boolean active;
    private boolean pinActive;
    private boolean venomActive;
    private boolean jumpBoostActive;
    private boolean telebowActive;

    public TimersFeature(Minecraft minecraft, ConfigCatalog configs, TimersConfig config,
            ClientSession session, ArrowTracker arrows, RelationLookup relations,
            PlayerNameCache playerNames, HudRegistry hudRegistry, TelebowCooldowns telebow,
            PlayerEntityTracker players, PlayerEquipmentAccess equipment) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.relations = relations;
        this.playerNames = playerNames;
        this.hudRegistry = hudRegistry;
        this.telebow = telebow;
        hud = new TimersHud(minecraft, config, renderer);
        pin = new PinTracker(config, arrows, this::onPin, () -> {
            pinTimers.clear();
            nextRefresh = 0;
        });
        venom = new VenomTracker(minecraft, config, session, players, equipment, this::onVenom,
                () -> {
                    venomTimers.clear();
                    nextRefresh = 0;
                });
        jumpBoost = new JumpBoostTracker(minecraft, config, session, players, equipment,
                this::onJumpBoost, player -> {
                    jumpBoostTimers.remove(player.getUniqueID());
                    nextRefresh = 0;
                }, () -> {
                    jumpBoostTimers.clear();
                    nextRefresh = 0;
                });
    }

    @Override
    public void start() {
        session.checkThread();
        if (started)
            return;
        started = true;
        hudRegistry.register(hud);
        configs.addListener(this);
        updateActivation();
    }

    @Override
    public void stop() {
        session.checkThread();
        started = false;
        configs.removeListener(this);
        updateActivation();
        hudRegistry.unregister(hud);
    }

    private void updateActivation() {
        boolean pin = started && config.enabled() && config.pinEnabled.get() && (config.pinOwn.get()
                || config.pinFriends.get() || config.pinEnemies.get() || config.pinOthers.get());
        boolean cooldowns = started && config.enabled() && config.telebowEnabled.get()
                && (config.telebowOwn.get() || config.telebowFriends.get()
                        || config.telebowEnemies.get() || config.telebowOthers.get());
        boolean poison = started && config.enabled() && config.venomEnabled.get()
                && (config.venomSelf.get() || config.venomTargets.get())
                && (config.venomOwn.get() || config.venomFriends.get() || config.venomEnemies.get()
                        || config.venomOthers.get());
        boolean jump = started && config.enabled() && config.jumpBoostEnabled.get()
                && (config.jumpBoostOwn.get() || config.jumpBoostFriends.get()
                        || config.jumpBoostEnemies.get() || config.jumpBoostOthers.get());
        if (pin != pinActive) {
            pinActive = pin;
            this.pin.active(pin);
        }
        if (cooldowns != telebowActive) {
            telebowActive = cooldowns;
            if (telebowActive) {
                telebow.addListener(this);
            } else {
                telebow.removeListener(this);
                onTelebowCooldownsReset();
            }
        }
        if (poison != venomActive) {
            venomActive = poison;
            venom.active(poison);
        }
        if (jump != jumpBoostActive) {
            jumpBoostActive = jump;
            jumpBoost.active(jump);
        }
        boolean enabled = pinActive || venomActive || jumpBoostActive || telebowActive;
        if (enabled == active)
            return;
        active = enabled;
        if (active) {
            ClientEvents.ATTACK.add(onAttackListener);
            ClientEvents.SOUND.add(onSoundListener);
            ClientEvents.TICK_END.add(onTickListener);
            ClientEvents.OVERLAY.add(onRenderOverlayListener);
        } else {
            ClientEvents.ATTACK.remove(onAttackListener);
            ClientEvents.SOUND.remove(onSoundListener);
            ClientEvents.TICK_END.remove(onTickListener);
            ClientEvents.OVERLAY.remove(onRenderOverlayListener);
            rows.clear();
            pendingRows.clear();
            countdownTexts.clear();
            hud.rows(rows);
        }
    }

    @Override
    public String countdowns(UUID playerId, int types) {
        session.checkThread();
        if (!active || pinTimers.isEmpty() && venomTimers.isEmpty() && jumpBoostTimers.isEmpty()
                && telebowCooldowns.isEmpty())
            return "";
        if (countdownTypes != types) {
            countdownTypes = types;
            countdownTexts.clear();
        }
        String cached = countdownTexts.get(playerId);
        if (cached != null && nextRefresh != 0)
            return cached;
        long now = System.nanoTime();
        StringBuilder text = new StringBuilder();
        if (pinActive && (types & PIN) != 0)
            appendCountdown(text, pinTimers.get(playerId), TimerType.PIN, now);
        if (venomActive && (types & VENOM) != 0)
            appendCountdown(text, venomTimers.get(playerId), TimerType.VENOM, now);
        if (jumpBoostActive && (types & JUMP_BOOST) != 0)
            appendCountdown(text, jumpBoostTimers.get(playerId), TimerType.JUMP_BOOST, now);
        if (telebowActive && (types & TELEBOW) != 0)
            appendCountdown(text, telebowCooldowns.get(playerId), TimerType.TELEBOW, now);
        String result = text.toString();
        if (nextRefresh != 0)
            countdownTexts.put(playerId, result);
        return result;
    }

    private void appendCountdown(StringBuilder text, Timer timer, TimerType type, long now) {
        if (timer != null && timer.row != null && now < timer.until
                && (type == TimerType.TELEBOW || timer.player.isEntityAlive())
                && accepts(timer.player, type)) {
            text.append(' ').append(type.color).append(timer.row.time);
        }
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started || !(changes.affects("debufftimers", "enabled")
                || changes.affects("debufftimers", "pin_enabled")
                || changes.affects("debufftimers", "own")
                || changes.affects("debufftimers", "friends")
                || changes.affects("debufftimers", "enemies")
                || changes.affects("debufftimers", "others")
                || changes.affects("debufftimers", "venom_enabled")
                || changes.affects("debufftimers", "venom_self")
                || changes.affects("debufftimers", "venom_targets")
                || changes.affects("debufftimers", "venom_own")
                || changes.affects("debufftimers", "venom_friends")
                || changes.affects("debufftimers", "venom_enemies")
                || changes.affects("debufftimers", "venom_others")
                || changes.affects("debufftimers", "jump_boost_enabled")
                || changes.affects("debufftimers", "jump_boost_own")
                || changes.affects("debufftimers", "jump_boost_friends")
                || changes.affects("debufftimers", "jump_boost_enemies")
                || changes.affects("debufftimers", "jump_boost_others")
                || changes.affects("debufftimers", "jump_boost_sound_required")
                || changes.affects("debufftimers", "jump_boost_reset_on_low_jump")
                || changes.affects("debufftimers", "telebow_enabled")
                || changes.affects("debufftimers", "telebow_own")
                || changes.affects("debufftimers", "telebow_friends")
                || changes.affects("debufftimers", "telebow_enemies")
                || changes.affects("debufftimers", "telebow_others")
                || changes.affects("debufftimers", "show_group_names")))
            return;
        updateActivation();
        if (changes.affects("debufftimers", "jump_boost_reset_on_low_jump")) {
            jumpBoost.resetJumpChecks();
            if (config.jumpBoostResetOnLowJump.get()) {
                for (Timer timer : jumpBoostTimers.values())
                    jumpBoost.trackJumps(timer.player, timer.until);
            }
        }
        venomTimers.values().removeIf(
                timer -> timer.venomSelf ? !config.venomSelf.get() : !config.venomTargets.get());
        nextRefresh = 0;
        hud.invalidate();
    }

    private boolean accepts(EntityPlayer player, TimerType type) {
        if (minecraft.thePlayer == null)
            return false;
        UUID playerId = player.getUniqueID();
        UUID ownId = minecraft.thePlayer.getUniqueID();
        switch (type) {
            case PIN:
                return relations.matches(playerId, ownId, config.pinOwn.get(),
                        config.pinFriends.get(), config.pinEnemies.get(), config.pinOthers.get());
            case VENOM:
                return relations.matches(playerId, ownId, config.venomOwn.get(),
                        config.venomFriends.get(), config.venomEnemies.get(),
                        config.venomOthers.get());
            case JUMP_BOOST:
                return relations.matches(playerId, ownId, config.jumpBoostOwn.get(),
                        config.jumpBoostFriends.get(), config.jumpBoostEnemies.get(),
                        config.jumpBoostOthers.get());
            default:
                return relations.matches(playerId, ownId, config.telebowOwn.get(),
                        config.telebowFriends.get(), config.telebowEnemies.get(),
                        config.telebowOthers.get());
        }
    }

    private void onVenom(EntityPlayer wearer, EntityPlayer target) {
        long now = System.nanoTime();
        if (config.venomSelf.get())
            recordVenom(wearer, now + 24_000_000_000L, true);
        if (config.venomTargets.get())
            recordVenom(target, now + 12_000_000_000L, false);
    }

    private void recordVenom(EntityPlayer player, long until, boolean self) {
        if (!player.isEntityAlive() || !accepts(player, TimerType.VENOM))
            return;
        UUID playerId = player.getUniqueID();
        Timer previous = venomTimers.get(playerId);
        if (previous == null || previous.player != player || until > previous.until) {
            Timer timer = new Timer(player, until);
            timer.venomSelf = self;
            venomTimers.put(playerId, timer);
            nextRefresh = 0;
        }
    }

    private void onAttack(Attack event) {
        if (venomActive && event.entityPlayer == minecraft.thePlayer
                && event.target instanceof EntityPlayer)
            venom.attack((EntityPlayer) event.target);
    }

    private void onSound(Sound event) {
        if (jumpBoostActive && config.jumpBoostSoundRequired.get()
                && event.entity == minecraft.thePlayer && "random.drink".equals(event.name))
            jumpBoost.drink(minecraft.thePlayer);
    }

    private void onJumpBoost(EntityPlayer player, long until) {
        if (!accepts(player, TimerType.JUMP_BOOST))
            return;
        jumpBoostTimers.put(player.getUniqueID(), new Timer(player, until));
        jumpBoost.trackJumps(player, until);
        nextRefresh = 0;
    }

    @Override
    public void onTelebowCooldown(EntityPlayer player, long until) {
        UUID playerId = player.getUniqueID();
        Timer timer = telebowCooldowns.get(playerId);
        if (timer == null || timer.player != player) {
            telebowCooldowns.put(playerId, new Timer(player, until));
        } else {
            timer.until = until;
        }
        nextRefresh = 0;
    }

    @Override
    public void onTelebowCooldownsReset() {
        telebowCooldowns.clear();
        nextRefresh = 0;
    }

    private void onPin(EntityPlayer player, long until) {
        if (!player.isEntityAlive() || !accepts(player, TimerType.PIN))
            return;
        UUID playerId = player.getUniqueID();
        Timer previous = pinTimers.get(playerId);
        if (previous == null || until > previous.until) {
            pinTimers.put(playerId, new Timer(player, until));
            nextRefresh = 0;
        }
    }

    private void onTick() {
        if (venomActive)
            venom.tick();
        if (jumpBoostActive)
            jumpBoost.tick();
        if (pinTimers.isEmpty() && venomTimers.isEmpty() && jumpBoostTimers.isEmpty()
                && telebowCooldowns.isEmpty() && hud.empty())
            return;
        long now = System.nanoTime();
        if (now < nextRefresh)
            return;
        nextRefresh = now + 100_000_000L;
        countdownTexts.clear();
        pendingRows.clear();
        boolean orderChanged = pinActive && appendRows(pinTimers, TimerType.PIN, now);
        orderChanged |= venomActive && appendRows(venomTimers, TimerType.VENOM, now);
        orderChanged |= jumpBoostActive && appendRows(jumpBoostTimers, TimerType.JUMP_BOOST, now);
        orderChanged |= telebowActive && appendRows(telebowCooldowns, TimerType.TELEBOW, now);
        if (orderChanged || pendingRows.size() != rows.size()) {
            pendingRows.sort(Comparator.comparing((TimersHud.Row row) -> row.type)
                    .thenComparing(row -> row.group).thenComparingLong(row -> row.until));
            List<TimersHud.Row> previous = rows;
            rows = pendingRows;
            pendingRows = previous;
        }
        hud.rows(rows);
    }

    private boolean appendRows(Map<UUID, Timer> timers, TimerType type, long now) {
        boolean orderChanged = false;
        Iterator<Timer> iterator = timers.values().iterator();
        while (iterator.hasNext()) {
            Timer timer = iterator.next();
            if (now >= timer.until || type != TimerType.TELEBOW && !timer.player.isEntityAlive()) {
                iterator.remove();
                continue;
            }
            if (accepts(timer.player, type)) {
                UUID playerId = timer.player.getUniqueID();
                Group group;
                if (playerId.equals(minecraft.thePlayer.getUniqueID())) {
                    group = Group.OWN;
                } else {
                    Relation relation = relations.relationOf(playerId);
                    group = relation == Relation.FRIEND ? Group.FRIEND
                            : relation == Relation.ENEMY ? Group.ENEMY : Group.OTHER;
                }
                String name = playerNames.displayName(playerId);
                if (name == null)
                    name = timer.player.getDisplayName().getFormattedText();
                if (timer.row == null) {
                    timer.row = new TimersHud.Row(type, group, name, timer.until - now);
                    orderChanged = true;
                } else {
                    orderChanged |= timer.row.group != group || timer.row.until != timer.until;
                    timer.row.update(group, name, timer.until - now);
                }
                timer.row.until = timer.until;
                pendingRows.add(timer.row);
            } else {
                timer.row = null;
            }
        }
        return orderChanged;
    }

    private void onRenderOverlay(Overlay event) {
        if (hud.empty()
                || hudRegistry.editing() || minecraft.theWorld == null
                || minecraft.gameSettings.hideGUI)
            return;
        ScaledResolution resolution = event.resolution;
        HudBounds bounds = HudRenderer.layout(hud, resolution.getScaledWidth(),
                resolution.getScaledHeight(), resolution.getScaleFactor(), false);
        hud.visibleHeight = (resolution.getScaledHeight() - bounds.y) / bounds.scale;
        renderer.render(hud, bounds, event.partialTicks, false);
    }

    private static final class Timer {
        final EntityPlayer player;
        long until;
        TimersHud.Row row;
        boolean venomSelf;

        Timer(EntityPlayer player, long until) {
            this.player = player;
            this.until = until;
        }
    }
}
