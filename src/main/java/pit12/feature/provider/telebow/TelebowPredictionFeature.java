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
package pit12.feature.provider.telebow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.client.relation.api.PlayerAudience;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationListener;
import pit12.feature.client.relation.api.RelationLookup;
import pit12.feature.provider.telebow.api.TelebowPrediction;
import pit12.runtime.arrow.ArrowAccess;
import pit12.runtime.arrow.ArrowHit;
import pit12.runtime.arrow.ArrowListener;
import pit12.runtime.arrow.ArrowTracker;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.PitSnapshot;
import pit12.runtime.pit.SpawnState;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.session.ClientSession;
import pit12.shared.entity.PlayerPositions;
import pit12.shared.lifecycle.DemandLifecycle;
import pit12.shared.lifecycle.DemandMask;
import pit12.shared.performance.HotPath;

/** Forge's generated event handler needs a public class. */
public final class TelebowPredictionFeature extends DemandLifecycle
        implements TelebowPrediction, ArrowListener, ConfigChangeListener, RelationListener {
    private final Minecraft minecraft;
    private final ClientSession session;
    private final ConfigCatalog configs;
    private final TelebowPredictionConfig settings;
    private final RelationLookup relations;
    private final ArrowTracker arrows;
    private final PlayerEntityTracker players;
    private final PitContext pit;
    private final DemandMask requests = new DemandMask();
    private final Map<UUID, Cooldown> cooldowns = new LinkedHashMap<>();
    private final Map<Integer, Shot> shots = new HashMap<>();
    private final List<Shot> attempts = new ArrayList<>();
    private int audience;
    private long tick;
    private long revision;

    public TelebowPredictionFeature(Minecraft minecraft, ClientSession session,
            ConfigCatalog configs, TelebowPredictionConfig settings, RelationLookup relations,
            ArrowTracker arrows, PlayerEntityTracker players, PitContext pit) {
        super(session::checkThread);
        this.minecraft = minecraft;
        this.session = session;
        this.configs = configs;
        this.settings = settings;
        this.relations = relations;
        this.arrows = arrows;
        this.players = players;
        this.pit = pit;
    }

    public IntegerSetting automaticMargin() {
        return settings.predictionMargin;
    }

    @Override
    public void setCooldownDemand(Object owner, int groups) {
        session.checkThread();
        int combined = requests.set(owner, groups);
        if (combined == audience)
            return;
        audience = combined;
        if (combined == 0)
            releaseOwner(requests);
        else if (isActive())
            refreshAudience();
        else
            retainOwner(requests);
    }

    @Override
    protected void activate() {
        players.retain(this);
        pit.retain(this);
        configs.addListener(this);
        relations.addListener(this);
        arrows.addListener(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    protected void deactivate() {
        MinecraftForge.EVENT_BUS.unregister(this);
        arrows.removeListener(this);
        relations.removeListener(this);
        configs.removeListener(this);
        pit.release(this);
        players.release(this);
        requests.clear();
        audience = 0;
        onArrowsReset();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (changes.affects(settings.id(), settings.cooldownDetection.id()))
            attempts.clear();
        else if (changes.affects(settings.id(), settings.correctCooldown.id())
                && !settings.correctCooldown.get())
            attempts.removeIf(shot -> shot.correction);
    }

    @Override
    public void onRelationsLoaded() {
        refreshAudience();
    }

    @Override
    public void onRelationChanged(UUID playerId, Relation previous, Relation current) {
        refreshAudience();
    }

    private void refreshAudience() {
        cooldowns.values().removeIf(cooldown -> {
            cooldown.group = groupOf(cooldown.player);
            return !PlayerAudience.matches(audience, cooldown.group);
        });
        shots.values().removeIf(shot -> !interested(shot.shooter));
        attempts.removeIf(shot -> !interested(shot.shooter));
        revision++;
    }

    private boolean interested(EntityPlayer player) {
        EntityPlayer local = minecraft.thePlayer;
        if (local == null)
            return false;
        if (audience == PlayerAudience.ALL)
            return true;
        if (player == local)
            return (audience & PlayerAudience.OWN) != 0;
        return (audience & PlayerAudience.REMOTE) == PlayerAudience.REMOTE
                || PlayerAudience.matches(audience, PlayerAudience.groupOf(player.getUniqueID(),
                        local.getUniqueID(), relations));
    }

    private int groupOf(EntityPlayer player) {
        EntityPlayer local = minecraft.thePlayer;
        return local == null ? 0
                : PlayerAudience.groupOf(player.getUniqueID(), local.getUniqueID(), relations);
    }

    @Override
    public long cooldownUntil(UUID playerId) {
        session.checkThread();
        if (cooldowns.isEmpty())
            return 0;
        Cooldown cooldown = cooldowns.get(playerId);
        return cooldown != null && cooldown.player.isEntityAlive() ? cooldown.until : 0;
    }

    @Override
    public long cooldownRevision() {
        session.checkThread();
        return revision;
    }

    @Override
    public void visitCooldowns(int groups, CooldownVisitor visitor) {
        session.checkThread();
        if (groups == 0 || cooldowns.isEmpty())
            return;
        long now = System.nanoTime();
        for (Cooldown cooldown : cooldowns.values()) {
            if (cooldown.until > now && cooldown.player.isEntityAlive()
                    && PlayerAudience.matches(groups, cooldown.group))
                visitor.accept(cooldown.player, cooldown.until, cooldown.group);
        }
    }

    @Override
    public boolean blocksArrow(UUID shooterId, int arrowId) {
        session.checkThread();
        if (cooldowns.isEmpty())
            return false;
        Cooldown cooldown = cooldowns.get(shooterId);
        return cooldown != null && cooldown.arrowId != arrowId && System.nanoTime() < cooldown.until
                && refreshCooldown(cooldown, pit.current());
    }

    @Override
    public boolean onArrowSpawn(EntityArrow arrow, EntityPlayer shooter, ItemStack bow,
            boolean sneaking, long lastSneakingNanos) {
        if (!interested(shooter))
            return false;
        long now = System.nanoTime();
        UUID shooterId = shooter.getUniqueID();
        Cooldown cooldown = cooldowns.get(shooterId);
        PitSnapshot snapshot = pit.current();
        if (cooldown != null && (now >= cooldown.until || !refreshCooldown(cooldown, snapshot))) {
            cooldowns.remove(shooterId);
            revision++;
            cooldown = null;
        }
        boolean attempt = sneaking || lastSneakingNanos != 0
                && now - lastSneakingNanos <= hitWindowNanos(shooter == minecraft.thePlayer);
        if (cooldown == null && !attempt) {
            boolean pending = false;
            for (int index = 0, size = attempts.size(); index < size; index++) {
                Shot shot = attempts.get(index);
                if (shot.shooter.getUniqueID().equals(shooterId)) {
                    pending = true;
                    break;
                }
            }
            if (!pending)
                return false;
        }
        int level = PitEnchantmentReader.levelOf(bow, PitEnchantment.Telebow);
        int seconds = level == 1 ? 90 : level == 2 ? 45 : level == 3 ? 20 : 0;
        if (seconds == 0)
            return false;
        Cooldown estimate = null;
        if (attempt && (cooldown == null || settings.correctCooldown.get())) {
            estimate = new Cooldown(shooter, arrow.getEntityId(), now,
                    now + seconds * 1_000_000_000L, snapshot);
            if (cooldown == null && settings.cooldownDetection.get() == 0) {
                recordCooldown(shooterId, estimate);
                estimate = null;
            }
        }
        Shot shot = new Shot(shooter, now, estimate, cooldown != null);
        shots.put(arrow.getEntityId(), shot);
        if (estimate != null)
            attempts.add(shot);
        return true;
    }

    @Override
    public long hitWindowNanos(boolean local) {
        return settings.predictionMargin.get() * (local ? 2_000_000L : 1_000_000L);
    }

    @Override
    public void onArrowRemoved(EntityArrow arrow, boolean serverRemoved) {
        Shot shot = shots.get(arrow.getEntityId());
        if (shot == null)
            return;
        boolean inGround = ((ArrowAccess) arrow).pit12$isInGround();
        long now = System.nanoTime();
        if (shot.estimate != null && attempts.contains(shot)) {
            if (serverRemoved || inGround || arrow.isDead) {
                shot.landedAt = now;
                shot.landedTick = tick;
                shot.landedPosition = new Vec3(arrow.posX, arrow.posY, arrow.posZ);
                if (matchesTeleport(shot)) {
                    attempts.remove(shot);
                    confirm(shot);
                }
            } else {
                attempts.remove(shot);
            }
        }
    }

    @Override
    public void onArrowHit(EntityPlayer target, List<ArrowHit> candidates, long hitAt) {
        EntityPlayer shooter = null;
        long firstShot = Long.MAX_VALUE;
        for (int index = 0, size = candidates.size(); index < size; index++) {
            ArrowHit candidate = candidates.get(index);
            Shot shot = shots.get(candidate.arrow.getEntityId());
            if (shot == null || target.getUniqueID().equals(shot.shooter.getUniqueID())
                    || shooter != null && !shooter.getUniqueID().equals(shot.shooter.getUniqueID()))
                return;
            shooter = shot.shooter;
            firstShot = Math.min(firstShot, shot.firedAt);
        }
        // Callers skip empty candidate lists, so the loop always assigns shooter.
        // noinspection DataFlowIssue
        Cooldown cooldown = cooldowns.get(shooter.getUniqueID());
        if (cooldown != null && System.nanoTime() < cooldown.until
                && firstShot >= cooldown.startedAt && refreshCooldown(cooldown, pit.current())) {
            cooldown.until -= 3_000_000_000L;
            revision++;
        }
        for (int index = 0, size = attempts.size(); index < size; index++) {
            Shot pending = attempts.get(index);
            if (pending.shooter.getUniqueID().equals(shooter.getUniqueID())
                    && firstShot >= pending.firedAt)
                pending.estimate.until -= 3_000_000_000L;
        }
    }

    @Override
    public void onArrowFinished(int entityId) {
        shots.remove(entityId);
    }

    @Override
    public void onShooterTeleport(EntityPlayer shooter, double x, double y, double z) {
        if (attempts.isEmpty())
            return;
        // Absolute position syncs also use teleport packets.
        if (PlayerPositions.serverDistanceSquared(shooter, minecraft.thePlayer, x, y, z) < 16)
            return;
        long now = System.nanoTime();
        Vec3 position = new Vec3(x, y, z);
        Shot latest = null;
        Iterator<Shot> iterator = attempts.iterator();
        while (iterator.hasNext()) {
            Shot shot = iterator.next();
            if (!shot.shooter.getUniqueID().equals(shooter.getUniqueID()))
                continue;
            shot.teleportedAt = now;
            shot.teleportedTick = tick;
            shot.teleportPosition = position;
            if (matchesTeleport(shot)) {
                if (latest == null || shot.firedAt > latest.firedAt)
                    latest = shot;
                iterator.remove();
            }
        }
        if (latest != null)
            confirm(latest);
    }

    private long teleportWindowNanos(boolean local) {
        return hitWindowNanos(local) + settings.teleportExtraMargin.get() * 1_000_000L;
    }

    @Override
    public boolean matchesTeleport(Vec3 landing, Vec3 destination, long timeDifference,
            boolean sameTick, boolean local) {
        return TelebowTeleportMatch.matches(landing, destination, timeDifference, sameTick,
                teleportWindowNanos(local));
    }

    private boolean matchesTeleport(Shot shot) {
        return shot.landedAt != 0 && shot.teleportedAt != 0
                && matchesTeleport(shot.landedPosition, shot.teleportPosition,
                        shot.landedAt - shot.teleportedAt, shot.landedTick == shot.teleportedTick,
                        shot.shooter == minecraft.thePlayer);
    }

    private void confirm(Shot shot) {
        if (System.nanoTime() >= shot.estimate.until
                || !refreshCooldown(shot.estimate, pit.current()))
            return;
        UUID playerId = shot.shooter.getUniqueID();
        Cooldown current = cooldowns.get(playerId);
        if (current != null && current.startedAt > shot.firedAt)
            return;
        // Flight time counts toward the cooldown.
        recordCooldown(playerId, shot.estimate);
        Iterator<Shot> iterator = attempts.iterator();
        while (iterator.hasNext()) {
            Shot pending = iterator.next();
            if (!pending.shooter.getUniqueID().equals(playerId))
                continue;
            if (pending.firedAt <= shot.firedAt || !settings.correctCooldown.get())
                iterator.remove();
            else
                pending.correction = true;
        }
    }

    private void recordCooldown(UUID playerId, Cooldown cooldown) {
        cooldown.group = groupOf(cooldown.player);
        cooldowns.put(playerId, cooldown);
        revision++;
    }

    private boolean refreshCooldown(Cooldown cooldown, PitSnapshot pit) {
        // A live replacement can hide the old entity's death.
        if (cooldown.player.getHealth() <= 0)
            return false;
        EntityPlayer player = players.loadedPlayer(cooldown.player.getUniqueID());
        if (player == null)
            player = cooldown.player;
        if (!player.isEntityAlive())
            return false;
        boolean inSpawn =
                pit.spawnStateAt(player.posX, player.posY, player.posZ) == SpawnState.IN_SPAWN;
        if (inSpawn && !cooldown.inSpawn)
            return false;
        cooldown.player = player;
        cooldown.inSpawn = inSpawn;
        return true;
    }

    @SubscribeEvent
    @HotPath
    public void onTick(ClientTickEvent event) {
        if (event.phase != Phase.END)
            return;
        tick++;
        if (cooldowns.isEmpty() && attempts.isEmpty())
            return;
        long now = System.nanoTime();
        PitSnapshot snapshot = pit.current();
        Iterator<Cooldown> iterator = cooldowns.values().iterator();
        while (iterator.hasNext()) {
            Cooldown cooldown = iterator.next();
            if (now >= cooldown.until || !refreshCooldown(cooldown, snapshot)) {
                iterator.remove();
                revision++;
            }
        }
        int remaining = 0;
        for (int index = 0, size = attempts.size(); index < size; index++) {
            Shot shot = attempts.get(index);
            long margin = teleportWindowNanos(shot.shooter == minecraft.thePlayer);
            if (shot.landedAt != 0
                    && (margin == 0 ? shot.landedTick != tick : now - shot.landedAt > margin)
                    || now >= shot.estimate.until || !refreshCooldown(shot.estimate, snapshot))
                continue;
            attempts.set(remaining++, shot);
        }
        for (int index = attempts.size() - 1; index >= remaining; index--)
            attempts.remove(index);
    }

    @Override
    public void onArrowsReset() {
        shots.clear();
        attempts.clear();
        cooldowns.clear();
        revision++;
    }

    private static final class Cooldown {
        EntityPlayer player;
        final int arrowId;
        final long startedAt;
        long until;
        int group;
        boolean inSpawn;

        Cooldown(EntityPlayer player, int arrowId, long startedAt, long until, PitSnapshot pit) {
            this.player = player;
            this.arrowId = arrowId;
            this.startedAt = startedAt;
            this.until = until;
            inSpawn =
                    pit.spawnStateAt(player.posX, player.posY, player.posZ) == SpawnState.IN_SPAWN;
        }
    }
    private static final class Shot {
        final EntityPlayer shooter;
        final long firedAt;
        final Cooldown estimate;
        boolean correction;
        long landedAt;
        long teleportedAt;
        long landedTick;
        long teleportedTick;
        Vec3 landedPosition;
        Vec3 teleportPosition;

        Shot(EntityPlayer shooter, long firedAt, Cooldown estimate, boolean correction) {
            this.shooter = shooter;
            this.firedAt = firedAt;
            this.estimate = estimate;
            this.correction = correction;
        }
    }
}
