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
package pit12.feature.provider.timertracking;

import static edu.umd.cs.findbugs.annotations.SuppressMatchType.EXACT;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.client.relation.api.PlayerAudience;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationListener;
import pit12.feature.client.relation.api.RelationLookup;
import pit12.feature.provider.timertracking.api.TimerKind;
import pit12.feature.provider.timertracking.api.TimerTracking;
import pit12.runtime.arrow.ArrowTracker;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.PitSnapshot;
import pit12.runtime.pit.SpawnState;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.session.ClientSession;
import pit12.shared.lifecycle.DemandLifecycle;
import pit12.shared.lifecycle.DemandMask;

public final class TimerTrackingFeature extends DemandLifecycle
        implements TimerTracking, ConfigChangeListener, RelationListener {
    private static final TimerKind[] KINDS = TimerKind.values();
    private final Minecraft minecraft;
    private final ClientSession session;
    private final PlayerEntityTracker players;
    private final PitContext pit;
    private final ConfigCatalog configs;
    private final TimerTrackingConfig settings;
    private final RelationLookup relations;
    private final DemandMask requests = new DemandMask();
    private final EnumMap<TimerKind, Map<UUID, Timer>> timers = new EnumMap<>(TimerKind.class);
    private final Map<UUID, Countdown> countdownTexts = new HashMap<>();
    private final int[] groups = new int[KINDS.length];
    private final PinTracker pin;
    private final VenomTracker venom;
    private final JumpBoostTracker jumpBoost;
    private boolean pinActive;
    private boolean venomActive;
    private boolean jumpActive;
    private int selection;
    private long nextRefresh;
    private long revision;
    private long textRevision;

    public TimerTrackingFeature(Minecraft minecraft, ClientSession session, ConfigCatalog configs,
            TimerTrackingConfig settings, RelationLookup relations, ArrowTracker arrows,
            PlayerEntityTracker players, PlayerEquipmentAccess equipment, PitContext pit) {
        super(session::checkThread);
        this.minecraft = minecraft;
        this.session = session;
        this.configs = configs;
        this.settings = settings;
        this.relations = relations;
        this.players = players;
        this.pit = pit;
        for (TimerKind kind : KINDS)
            timers.put(kind, new LinkedHashMap<>());
        pin = new PinTracker(settings, arrows, player -> accepts(player, TimerKind.PIN),
                (player, until) -> record(TimerKind.PIN, player, until),
                () -> clear(TimerKind.PIN));
        venom = new VenomTracker(minecraft, settings, session, players, equipment, this::onVenom,
                () -> {
                    clear(TimerKind.VENOM_WEARER);
                    clear(TimerKind.VENOM_TARGET);
                });
        jumpBoost = new JumpBoostTracker(minecraft, settings, session, players, equipment,
                player -> accepts(player, TimerKind.JUMP_BOOST),
                () -> (groups[TimerKind.JUMP_BOOST.ordinal()] & PlayerAudience.REMOTE) != 0,
                this::onJumpBoost, player -> {
                    timers.get(TimerKind.JUMP_BOOST).remove(player.getUniqueID());
                    countdownTexts.remove(player.getUniqueID());
                    changed();
                }, () -> clear(TimerKind.JUMP_BOOST));
    }

    private void onJumpBoost(EntityPlayer player, long until) {
        record(TimerKind.JUMP_BOOST, player, until);
        if (accepts(player, TimerKind.JUMP_BOOST))
            jumpBoost.trackJumps(player, until);
    }

    public IntegerSetting[] automaticMargins() {
        return new IntegerSetting[] {settings.pinHitWindow, settings.venomHitWindow,
                settings.jumpBoostGrace};
    }

    @Override
    public void setDemand(Object owner, int demand) {
        session.checkThread();
        int combined = requests.set(owner, demand);
        if (selection == combined)
            return;
        selection = combined;
        for (TimerKind kind : KINDS)
            groups[kind.ordinal()] = kind.groups(selection);
        boolean wasActive = isActive();
        if (selection == 0)
            releaseOwner(requests);
        else
            retainOwner(requests);
        if (wasActive && isActive()) {
            updateTracking();
            refreshAudience();
        }
        changed();
    }

    @Override
    protected void activate() {
        players.retain(this);
        pit.retain(this);
        configs.addListener(this);
        relations.addListener(this);
        MinecraftForge.EVENT_BUS.register(this);
        updateTracking();
    }

    @Override
    protected void deactivate() {
        updateTracking();
        MinecraftForge.EVENT_BUS.unregister(this);
        relations.removeListener(this);
        configs.removeListener(this);
        pit.release(this);
        players.release(this);
        requests.clear();
        selection = 0;
        for (TimerKind kind : KINDS) {
            groups[kind.ordinal()] = 0;
            timers.get(kind).clear();
        }
        countdownTexts.clear();
        changed();
    }

    private void updateTracking() {
        boolean enabled = isActive();
        boolean next = enabled && groups[TimerKind.PIN.ordinal()] != 0;
        if (pinActive != next) {
            pinActive = next;
            pin.active(next);
        }
        next = enabled && (groups[TimerKind.VENOM_WEARER.ordinal()]
                | groups[TimerKind.VENOM_TARGET.ordinal()]) != 0;
        if (venomActive != next) {
            venomActive = next;
            venom.active(next);
        }
        next = enabled && groups[TimerKind.JUMP_BOOST.ordinal()] != 0;
        if (jumpActive != next) {
            jumpActive = next;
            jumpBoost.active(next);
        }
    }

    @Override
    public void onRelationsLoaded() {
        refreshAudience();
    }

    @Override
    public void onRelationChanged(UUID id, Relation previous, Relation current) {
        refreshAudience();
    }

    private void refreshAudience() {
        if (!isActive())
            return;
        for (TimerKind kind : KINDS)
            timers.get(kind).values().removeIf(timer -> {
                timer.group = groupOf(timer.player);
                return !PlayerAudience.matches(groups[kind.ordinal()], timer.group);
            });
        if (jumpActive)
            jumpBoost.refreshAudience();
        countdownTexts.clear();
        changed();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!changes.affects(settings.id(), settings.jumpBoostResetOnLowJump.id())
                && !changes.affects(settings.id(), settings.jumpBoostSoundRequired.id()))
            return;
        if (jumpActive) {
            jumpBoost.resetJumpChecks();
            jumpBoost.refreshAudience();
            if (settings.jumpBoostResetOnLowJump.get())
                for (Timer timer : timers.get(TimerKind.JUMP_BOOST).values())
                    jumpBoost.trackJumps(timer.player, timer.until);
        }
        changed();
    }

    private boolean accepts(EntityPlayer player, int audience) {
        if (audience == 0 || minecraft.thePlayer == null)
            return false;
        if (audience == PlayerAudience.ALL)
            return true;
        if (player == minecraft.thePlayer)
            return (audience & PlayerAudience.OWN) != 0;
        return (audience & PlayerAudience.REMOTE) == PlayerAudience.REMOTE
                || PlayerAudience.matches(audience, groupOf(player));
    }

    private boolean accepts(EntityPlayer player, TimerKind kind) {
        return accepts(player, groups[kind.ordinal()]);
    }

    private int groupOf(EntityPlayer player) {
        if (minecraft.thePlayer == null)
            return 0;
        return PlayerAudience.groupOf(player.getUniqueID(), minecraft.thePlayer.getUniqueID(),
                relations);
    }

    private void onVenom(EntityPlayer wearer, EntityPlayer target) {
        long now = System.nanoTime();
        record(TimerKind.VENOM_WEARER, wearer, now + 24_000_000_000L);
        record(TimerKind.VENOM_TARGET, target, now + 12_000_000_000L);
    }

    private void record(TimerKind kind, EntityPlayer player, long until) {
        if (!player.isEntityAlive() || !accepts(player, kind))
            return;
        Map<UUID, Timer> current = timers.get(kind);
        UUID id = player.getUniqueID();
        Timer previous = current.get(id);
        if (previous == null || previous.player != player)
            current.put(id, new Timer(player, until, pit.current(), groupOf(player)));
        else if (until > previous.until) {
            previous.until = until;
            previous.inSpawn = pit.current().spawnStateAt(player.posX, player.posY,
                    player.posZ) == SpawnState.IN_SPAWN;
        } else
            return;
        changed();
    }

    private void clear(TimerKind kind) {
        timers.get(kind).clear();
        countdownTexts.clear();
        changed();
    }

    private void changed() {
        revision++;
        textRevision++;
        nextRefresh = 0;
    }

    @Override
    public long revision() {
        session.checkThread();
        return revision;
    }

    /** Visits matching timers. Venom uses the longer of the selected wearer and target durations. */
    @Override
    public void visit(int selection, Visitor visitor) {
        session.checkThread();
        long now = System.nanoTime();
        for (TimerKind kind : KINDS) {
            int audience = kind.groups(selection);
            if (audience == 0 || timers.get(kind).isEmpty())
                continue;
            for (Timer timer : timers.get(kind).values()) {
                if (timer.until <= now || !timer.player.isEntityAlive())
                    continue;
                UUID id = timer.player.getUniqueID();
                int group = timer.group;
                if (!PlayerAudience.matches(audience, group))
                    continue;
                long until = timer.until;
                if (kind == TimerKind.VENOM_TARGET) {
                    Timer wearer = timers.get(TimerKind.VENOM_WEARER).get(id);
                    if (wearer != null && wearer.until > now && wearer.player.isEntityAlive()
                            && PlayerAudience.matches(TimerKind.VENOM_WEARER.groups(selection),
                                    group))
                        continue;
                } else if (kind == TimerKind.VENOM_WEARER) {
                    Timer target = timers.get(TimerKind.VENOM_TARGET).get(id);
                    if (target != null && target.player.isEntityAlive() && PlayerAudience
                            .matches(TimerKind.VENOM_TARGET.groups(selection), group))
                        until = Math.max(until, target.until);
                }
                visitor.accept(kind, timer.player, until, group);
            }
        }
    }

    @Override
    @SuppressFBWarnings(value = "NP_LOAD_OF_KNOWN_NULL_VALUE", matchType = EXACT)
    public String countdowns(UUID playerId, int types) {
        session.checkThread();
        if (!isActive())
            return "";
        Countdown cached = countdownTexts.get(playerId);
        if (cached != null && cached.revision == textRevision && cached.types == types)
            return cached.text;
        long now = System.nanoTime();
        StringBuilder text = null;
        if ((types & PIN) != 0)
            text = append(text, timers.get(TimerKind.PIN).get(playerId), TimerKind.PIN, now);
        if ((types & VENOM) != 0) {
            Timer wearer = timers.get(TimerKind.VENOM_WEARER).get(playerId);
            Timer target = timers.get(TimerKind.VENOM_TARGET).get(playerId);
            text = append(text,
                    wearer == null || target != null && target.until > wearer.until ? target
                            : wearer,
                    TimerKind.VENOM_TARGET, now);
        }
        if ((types & JUMP_BOOST) != 0)
            text = append(text, timers.get(TimerKind.JUMP_BOOST).get(playerId),
                    TimerKind.JUMP_BOOST, now);
        if (text == null) {
            countdownTexts.remove(playerId);
            return "";
        }
        String result = text.toString();
        if (cached == null) {
            cached = new Countdown();
            countdownTexts.put(playerId, cached);
        }
        cached.types = types;
        cached.revision = textRevision;
        cached.text = result;
        return result;
    }

    private static StringBuilder append(StringBuilder text, Timer timer, TimerKind kind, long now) {
        if (timer != null && now < timer.until && timer.player.isEntityAlive()) {
            if (text == null)
                text = new StringBuilder();
            long tenths = (timer.until - now + 99_999_999L) / 100_000_000L;
            text.append(' ').append(kind.color).append(tenths / 10).append('.').append(tenths % 10)
                    .append('s');
        }
        return text;
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (venomActive && event.entityPlayer == minecraft.thePlayer
                && event.target instanceof EntityPlayer)
            venom.attack((EntityPlayer) event.target);
    }

    @SubscribeEvent
    public void onSound(PlaySoundAtEntityEvent event) {
        if (jumpActive && event.entity == minecraft.thePlayer
                && settings.jumpBoostSoundRequired.get() && "random.drink".equals(event.name))
            jumpBoost.drink(minecraft.thePlayer);
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase != Phase.END)
            return;
        if (venomActive)
            venom.tick();
        if (jumpActive)
            jumpBoost.tick();
        boolean hasTimers = false;
        for (TimerKind kind : KINDS)
            if (!timers.get(kind).isEmpty()) {
                hasTimers = true;
                break;
            }
        if (!hasTimers)
            return;
        long now = System.nanoTime();
        if (now < nextRefresh)
            return;
        nextRefresh = now + 100_000_000L;
        textRevision++;
        PitSnapshot snapshot = pit.current();
        for (TimerKind kind : KINDS) {
            if (timers.get(kind).isEmpty())
                continue;
            Iterator<Timer> iterator = timers.get(kind).values().iterator();
            while (iterator.hasNext()) {
                Timer timer = iterator.next();
                if (now >= timer.until || timer.player.getHealth() <= 0) {
                    iterator.remove();
                    revision++;
                    continue;
                }
                EntityPlayer player = players.loadedPlayer(timer.player.getUniqueID());
                if (player == null)
                    player = timer.player;
                boolean inSpawn = snapshot.spawnStateAt(player.posX, player.posY,
                        player.posZ) == SpawnState.IN_SPAWN;
                if (!player.isEntityAlive() || inSpawn && !timer.inSpawn
                        || !PlayerAudience.matches(groups[kind.ordinal()], timer.group)) {
                    iterator.remove();
                    revision++;
                    continue;
                }
                if (player != timer.player && kind == TimerKind.JUMP_BOOST)
                    jumpBoost.trackJumps(player, timer.until);
                timer.player = player;
                timer.inSpawn = inSpawn;
            }
        }
        countdownTexts.keySet().removeIf(id -> {
            for (TimerKind kind : KINDS)
                if (timers.get(kind).containsKey(id))
                    return false;
            return true;
        });
    }

    private static final class Countdown {
        int types;
        long revision;
        String text;
    }
    private static final class Timer {
        EntityPlayer player;
        long until;
        boolean inSpawn;
        int group;

        Timer(EntityPlayer player, long until, PitSnapshot pit, int group) {
            this.player = player;
            this.until = until;
            this.group = group;
            inSpawn =
                    pit.spawnStateAt(player.posX, player.posY, player.posZ) == SpawnState.IN_SPAWN;
        }
    }
}
