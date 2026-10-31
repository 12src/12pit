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
package pit12.feature.render.timers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationLookup;
import pit12.feature.render.telebow.api.TelebowCooldownListener;
import pit12.feature.render.telebow.api.TelebowCooldowns;
import pit12.feature.render.timers.TimersHud.Group;
import pit12.feature.render.timers.TimersHud.TimerType;
import pit12.feature.render.timers.api.PlayerTimers;
import pit12.runtime.arrow.ArrowTracker;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.Setting;
import pit12.runtime.hud.HudBounds;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.hud.HudRenderer;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.PitSnapshot;
import pit12.runtime.pit.SpawnState;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerNameCache;
import pit12.runtime.session.ClientSession;
import pit12.shared.lifecycle.ClientLifecycle;

public final class TimersFeature
        implements ClientLifecycle, ConfigChangeListener, TelebowCooldownListener, PlayerTimers {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final TimersConfig config;
    private final ClientSession session;
    private final PlayerEntityTracker players;
    private final PitContext pit;
    private final RelationLookup relations;
    private final PlayerNameCache playerNames;
    private final HudRegistry hudRegistry;
    private final TelebowCooldowns telebow;
    private final HudRenderer renderer = new HudRenderer();
    private final TimersHud[] huds = new TimersHud[TimerType.values().length + 1];
    private final List<TimersHud> activeHuds = new ArrayList<>();
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
            PlayerEntityTracker players, PlayerEquipmentAccess equipment, PitContext pit) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.players = players;
        this.pit = pit;
        this.relations = relations;
        this.playerNames = playerNames;
        this.hudRegistry = hudRegistry;
        this.telebow = telebow;
        huds[0] = new TimersHud(minecraft, config, renderer, null);
        for (TimerType type : TimerType.values()) {
            huds[type.ordinal() + 1] = new TimersHud(minecraft, config, renderer, type);
        }
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
        updateHudLayout();
        configs.addListener(this);
        updateActivation();
    }

    @Override
    public void stop() {
        session.checkThread();
        started = false;
        configs.removeListener(this);
        updateActivation();
        for (TimersHud hud : huds) {
            hudRegistry.unregister(hud);
        }
        activeHuds.clear();
    }

    private void updateHudLayout() {
        activeHuds.clear();
        for (TimersHud hud : huds) {
            if (hud.active()) {
                hudRegistry.register(hud);
                activeHuds.add(hud);
            } else {
                hudRegistry.unregister(hud);
            }
        }
        updateHudRows();
    }

    private void updateHudRows() {
        for (TimersHud hud : huds) {
            hud.rows.clear();
            hud.invalidate();
        }
        for (TimersHud.Row row : rows) {
            huds[config.hudGroups.get(row.type).independent() ? row.type.ordinal() + 1 : 0].rows
                    .add(row);
        }
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
            MinecraftForge.EVENT_BUS.register(this);
        } else {
            MinecraftForge.EVENT_BUS.unregister(this);
            rows.clear();
            pendingRows.clear();
            countdownTexts.clear();
            updateHudRows();
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
        if (timer != null && timer.row != null && now < timer.until && timer.player.isEntityAlive()
                && accepts(timer.player, type)) {
            text.append(' ').append(type.color).append(timer.row.time);
        }
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started) {
            return;
        }
        for (Setting<?> setting : config.settings()) {
            if (changes.affects("debufftimers", setting.id())) {
                for (TimersHud hud : huds) {
                    hud.invalidate();
                }
                break;
            }
        }
        for (TimerType type : TimerType.values()) {
            if (changes.affects("debufftimers", "debuff_timers." + type.id + ".independent")) {
                updateHudLayout();
                break;
            }
        }
        if (!(changes.affects("debufftimers", "enabled")
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
            Timer timer = new Timer(player, until, pit.current());
            timer.venomSelf = self;
            venomTimers.put(playerId, timer);
            nextRefresh = 0;
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (venomActive && event.entityPlayer == minecraft.thePlayer
                && event.target instanceof EntityPlayer)
            venom.attack((EntityPlayer) event.target);
    }

    @SubscribeEvent
    public void onSound(PlaySoundAtEntityEvent event) {
        // Config reads require the client thread, so filter integrated server events first.
        if (!jumpBoostActive || event.entity != minecraft.thePlayer)
            return;
        if (config.jumpBoostSoundRequired.get() && "random.drink".equals(event.name))
            jumpBoost.drink(minecraft.thePlayer);
    }

    private void onJumpBoost(EntityPlayer player, long until) {
        if (!accepts(player, TimerType.JUMP_BOOST))
            return;
        jumpBoostTimers.put(player.getUniqueID(), new Timer(player, until, pit.current()));
        jumpBoost.trackJumps(player, until);
        nextRefresh = 0;
    }

    @Override
    public void onTelebowCooldown(EntityPlayer player, long until) {
        UUID playerId = player.getUniqueID();
        Timer timer = telebowCooldowns.get(playerId);
        if (timer == null || timer.player != player) {
            telebowCooldowns.put(playerId, new Timer(player, until, pit.current()));
        } else {
            timer.until = until;
            timer.inSpawn = pit.current().spawnStateAt(player.posX, player.posY,
                    player.posZ) == SpawnState.IN_SPAWN;
        }
        nextRefresh = 0;
    }

    @Override
    public void onTelebowCooldownRemoved(UUID playerId) {
        telebowCooldowns.remove(playerId);
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
            pinTimers.put(playerId, new Timer(player, until, pit.current()));
            nextRefresh = 0;
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase != Phase.END)
            return;
        if (venomActive)
            venom.tick();
        if (jumpBoostActive)
            jumpBoost.tick();
        if (pinTimers.isEmpty() && venomTimers.isEmpty() && jumpBoostTimers.isEmpty()
                && telebowCooldowns.isEmpty() && rows.isEmpty())
            return;
        long now = System.nanoTime();
        if (now < nextRefresh) {
            boolean expired = false;
            boolean changed = false;
            for (TimersHud hud : activeHuds) {
                boolean hudChanged = false;
                for (TimersHud.Row row : hud.rows) {
                    if (now >= row.until) {
                        expired = true;
                    } else {
                        hudChanged |= row.updateTime(row.until - now);
                    }
                }
                if (hudChanged) {
                    hud.invalidate();
                    changed = true;
                }
            }
            if (!expired) {
                if (changed)
                    countdownTexts.clear();
                return;
            }
        }
        nextRefresh = now + 100_000_000L;
        countdownTexts.clear();
        pendingRows.clear();
        PitSnapshot snapshot = pit.current();
        boolean orderChanged = pinActive && appendRows(pinTimers, TimerType.PIN, now, snapshot);
        orderChanged |= venomActive && appendRows(venomTimers, TimerType.VENOM, now, snapshot);
        orderChanged |=
                jumpBoostActive && appendRows(jumpBoostTimers, TimerType.JUMP_BOOST, now, snapshot);
        orderChanged |=
                telebowActive && appendRows(telebowCooldowns, TimerType.TELEBOW, now, snapshot);
        if (orderChanged || pendingRows.size() != rows.size()) {
            pendingRows.sort(Comparator.comparing((TimersHud.Row row) -> row.type)
                    .thenComparing(row -> row.group).thenComparingLong(row -> row.until));
            List<TimersHud.Row> previous = rows;
            rows = pendingRows;
            pendingRows = previous;
        }
        updateHudRows();
    }

    private boolean appendRows(Map<UUID, Timer> timers, TimerType type, long now, PitSnapshot pit) {
        boolean orderChanged = false;
        Iterator<Timer> iterator = timers.values().iterator();
        while (iterator.hasNext()) {
            Timer timer = iterator.next();
            // A live replacement can hide the old entity's death.
            if (now >= timer.until || timer.player.getHealth() <= 0) {
                iterator.remove();
                continue;
            }
            UUID playerId = timer.player.getUniqueID();
            EntityPlayer player = players.loadedPlayer(playerId);
            if (player == null)
                player = timer.player;
            boolean inSpawn =
                    pit.spawnStateAt(player.posX, player.posY, player.posZ) == SpawnState.IN_SPAWN;
            if (!player.isEntityAlive() || inSpawn && !timer.inSpawn) {
                iterator.remove();
                continue;
            }
            if (player != timer.player && type == TimerType.JUMP_BOOST)
                jumpBoost.trackJumps(player, timer.until);
            timer.player = player;
            timer.inSpawn = inSpawn;
            if (accepts(timer.player, type)) {
                Group group;
                if (playerId.equals(minecraft.thePlayer.getUniqueID())) {
                    group = Group.OWN;
                } else {
                    Relation relation = relations.relationOf(playerId);
                    group = relation == Relation.FRIEND ? Group.FRIEND
                            : relation == Relation.ENEMY ? Group.ENEMY : Group.OTHER;
                }
                String name = playerNames.shortName(playerId);
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

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || rows.isEmpty()
                || hudRegistry.editing() || minecraft.theWorld == null
                || minecraft.gameSettings.hideGUI || !HudRenderer.visible(config.hud))
            return;
        ScaledResolution resolution = event.resolution;
        for (TimersHud hud : activeHuds) {
            if (hud.rows.isEmpty()) {
                continue;
            }
            HudBounds bounds = HudRenderer.layout(hud, resolution.getScaledWidth(),
                    resolution.getScaledHeight(), resolution.getScaleFactor(), false);
            hud.visibleHeight = (resolution.getScaledHeight() - bounds.y) / bounds.scale;
            renderer.render(hud, bounds, event.partialTicks, false);
        }
    }

    private static final class Timer {
        EntityPlayer player;
        long until;
        TimersHud.Row row;
        boolean venomSelf;
        boolean inSpawn;

        Timer(EntityPlayer player, long until, PitSnapshot pit) {
            this.player = player;
            this.until = until;
            inSpawn =
                    pit.spawnStateAt(player.posX, player.posY, player.posZ) == SpawnState.IN_SPAWN;
        }
    }
}
