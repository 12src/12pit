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
package pit12.feature.telebow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.telebow.api.TelebowCooldownListener;
import pit12.feature.telebow.api.TelebowCooldowns;
import pit12.runtime.arrow.ArrowAccess;
import pit12.runtime.arrow.ArrowHit;
import pit12.runtime.arrow.ArrowListener;
import pit12.runtime.arrow.ArrowTracker;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.PitSnapshot;
import pit12.runtime.pit.SpawnState;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.session.ClientSession;
import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.ClientLifecycle;

public final class TelebowCooldownTracker
        implements ClientLifecycle, ConfigChangeListener, ArrowListener, TelebowCooldowns {
    private static final long TELEPORT_WINDOW_NANOS = 1_000_000_000L;
    private final ConfigCatalog configs;
    private final TelebowConfig config;
    private final ClientSession session;
    private final ArrowTracker arrows;
    private final PlayerEntityTracker players;
    private final PitContext pit;
    private final List<TelebowCooldownListener> listeners = new ArrayList<>();
    private final Map<UUID, Cooldown> cooldowns = new HashMap<>();
    private final Map<Integer, Shot> shots = new HashMap<>();
    private final List<Shot> attempts = new ArrayList<>();
    private boolean started;
    private boolean active;

    TelebowCooldownTracker(ConfigCatalog configs, TelebowConfig config, ClientSession session,
            ArrowTracker arrows, PlayerEntityTracker players, PitContext pit) {
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.arrows = arrows;
        this.players = players;
        this.pit = pit;
    }

    @Override
    public void start() {
        session.checkThread();
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
        listeners.clear();
    }

    private void updateActivation() {
        boolean enabled = started && config.enabled();
        if (active == enabled)
            return;
        active = enabled;
        if (active) {
            arrows.addListener(this);
            MinecraftForge.EVENT_BUS.register(this);
        } else {
            MinecraftForge.EVENT_BUS.unregister(this);
            arrows.removeListener(this);
            onArrowsReset();
        }
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (changes.affects("telebow", "enabled"))
            updateActivation();
        if (changes.affects("telebow", "correct_cooldown") && !config.correctCooldown.get())
            attempts.clear();
    }

    @Override
    public void addListener(TelebowCooldownListener listener) {
        session.checkThread();
        if (listeners.contains(listener))
            return;
        listeners.add(listener);
        long now = System.nanoTime();
        PitSnapshot snapshot = pit.current();
        for (Cooldown cooldown : cooldowns.values()) {
            if (now < cooldown.until && refreshCooldown(cooldown, snapshot))
                listener.onTelebowCooldown(cooldown.player, cooldown.until);
        }
    }

    @Override
    public void removeListener(TelebowCooldownListener listener) {
        session.checkThread();
        listeners.remove(listener);
    }

    boolean blocksArrow(UUID shooterId, int arrowId) {
        Cooldown cooldown = cooldowns.get(shooterId);
        return cooldown != null && cooldown.arrowId != arrowId && System.nanoTime() < cooldown.until
                && refreshCooldown(cooldown, pit.current());
    }

    @Override
    public boolean onArrowSpawn(EntityArrow arrow, EntityPlayer shooter, ItemStack bow,
            boolean sneaking, long lastSneakingNanos) {
        long now = System.nanoTime();
        UUID shooterId = shooter.getUniqueID();
        Cooldown cooldown = cooldowns.get(shooterId);
        PitSnapshot snapshot = pit.current();
        if (cooldown != null && (now >= cooldown.until || !refreshCooldown(cooldown, snapshot))) {
            cooldowns.remove(shooterId);
            Listeners.notify(listeners, listener -> listener.onTelebowCooldownRemoved(shooterId));
            cooldown = null;
        }
        boolean attempt = sneaking || lastSneakingNanos != 0
                && now - lastSneakingNanos <= config.sneakWindow.get() * 1_000_000L;
        if (cooldown == null && !attempt)
            return false;
        int level = PitEnchantmentReader.levelOf(bow, PitEnchantment.Telebow);
        int seconds = level == 1 ? 90 : level == 2 ? 45 : level == 3 ? 20 : 0;
        if (seconds == 0)
            return false;
        Cooldown retry = null;
        if (cooldown == null) {
            cooldown = new Cooldown(shooter, arrow.getEntityId(), now,
                    now + seconds * 1_000_000_000L, snapshot);
            cooldowns.put(shooterId, cooldown);
            publish(cooldown);
        } else if (attempt && config.correctCooldown.get()) {
            retry = new Cooldown(shooter, arrow.getEntityId(), now, now + seconds * 1_000_000_000L,
                    snapshot);
        }
        Shot shot = new Shot(shooter, now, retry);
        shots.put(arrow.getEntityId(), shot);
        if (retry != null)
            attempts.add(shot);
        return true;
    }

    @Override
    public long hitWindowNanos() {
        return config.sneakWindow.get() * 1_000_000L;
    }

    @Override
    public void onArrowRemoved(EntityArrow arrow, boolean serverRemoved) {
        Shot shot = shots.get(arrow.getEntityId());
        if (shot == null)
            return;
        boolean inGround = ((ArrowAccess) arrow).pit12$isInGround();
        long now = System.nanoTime();
        if (shot.retry != null && attempts.contains(shot)) {
            if (serverRemoved || inGround || arrow.isDead) {
                shot.landedAt = now;
                if (shot.teleportedAt != 0 && now - shot.teleportedAt <= TELEPORT_WINDOW_NANOS) {
                    confirm(shot);
                    attempts.remove(shot);
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
        for (ArrowHit candidate : candidates) {
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
            publish(cooldown);
        }
        for (Shot pending : attempts) {
            if (pending.shooter.getUniqueID().equals(shooter.getUniqueID())
                    && firstShot >= pending.firedAt)
                pending.retry.until -= 3_000_000_000L;
        }
    }

    @Override
    public void onArrowFinished(int entityId) {
        shots.remove(entityId);
    }

    @Override
    public void onShooterTeleport(EntityPlayer shooter, double distanceSquared) {
        // Absolute position sync also uses teleport packets.
        if (distanceSquared < 16)
            return;
        long now = System.nanoTime();
        Shot latest = null;
        Iterator<Shot> iterator = attempts.iterator();
        while (iterator.hasNext()) {
            Shot shot = iterator.next();
            if (!shot.shooter.getUniqueID().equals(shooter.getUniqueID()))
                continue;
            // Teleport and arrow removal packets may arrive in either order.
            shot.teleportedAt = now;
            if (shot.landedAt != 0 && now - shot.landedAt <= TELEPORT_WINDOW_NANOS) {
                latest = shot;
                iterator.remove();
            }
        }
        if (latest != null)
            confirm(latest);
    }

    private void confirm(Shot shot) {
        if (!refreshCooldown(shot.retry, pit.current()))
            return;
        UUID playerId = shot.shooter.getUniqueID();
        Cooldown current = cooldowns.get(playerId);
        if (current != null && current.startedAt > shot.firedAt)
            return;
        // Flight time counts toward the cooldown.
        cooldowns.put(playerId, shot.retry);
        publish(shot.retry);
    }

    private void publish(Cooldown cooldown) {
        Listeners.notify(listeners,
                listener -> listener.onTelebowCooldown(cooldown.player, cooldown.until));
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
    public void onTick(ClientTickEvent event) {
        if (event.phase != Phase.END || cooldowns.isEmpty() && attempts.isEmpty())
            return;
        long now = System.nanoTime();
        PitSnapshot snapshot = pit.current();
        Iterator<Map.Entry<UUID, Cooldown>> iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Cooldown> entry = iterator.next();
            Cooldown cooldown = entry.getValue();
            if (now >= cooldown.until || !refreshCooldown(cooldown, snapshot)) {
                iterator.remove();
                Listeners.notify(listeners,
                        listener -> listener.onTelebowCooldownRemoved(entry.getKey()));
            }
        }
        attempts.removeIf(shot -> shot.landedAt != 0 && now - shot.landedAt > TELEPORT_WINDOW_NANOS
                || !refreshCooldown(shot.retry, snapshot));
    }

    @Override
    public void onArrowsReset() {
        shots.clear();
        attempts.clear();
        cooldowns.clear();
        Listeners.notify(listeners, TelebowCooldownListener::onTelebowCooldownsReset);
    }

    private static final class Cooldown {
        EntityPlayer player;
        final int arrowId;
        final long startedAt;
        long until;
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
        final Cooldown retry;
        long landedAt;
        long teleportedAt;

        Shot(EntityPlayer shooter, long firedAt, Cooldown retry) {
            this.shooter = shooter;
            this.firedAt = firedAt;
            this.retry = retry;
        }
    }
}
