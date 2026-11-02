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
package pit12.feature.client.prediction;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;
import net.minecraft.network.play.server.S37PacketStatistics;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.config.Setting;
import pit12.runtime.session.ClientSession;
import pit12.shared.lifecycle.ClientLifecycle;

public final class PredictionMarginsFeature implements ClientLifecycle, ConfigChangeListener {
    private final ConfigCatalog settings;
    private final BooleanSetting enabled;
    private final ConfigCatalog configs;
    private final ClientSession session;
    private final Runnable sessionChanged = this::schedule;
    private NetworkManager network;
    private Probe probe;
    private long revision;
    private long sampleAt;
    private long expiresAt;
    private int lastAttempt;
    private boolean started;

    public PredictionMarginsFeature(ConfigCatalog settings, BooleanSetting enabled,
            ConfigCatalog configs, ClientSession session) {
        this.settings = settings;
        this.enabled = enabled;
        this.configs = configs;
        this.session = session;
    }

    @Override
    public void start() {
        session.checkThread();
        started = true;
        settings.addListener(this);
        session.addListener(sessionChanged);
        schedule();
    }

    @Override
    public void stop() {
        session.checkThread();
        started = false;
        settings.removeListener(this);
        session.removeListener(sessionChanged);
        cancel();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (changes.affects("webui", "auto_prediction_margins"))
            schedule();
    }

    private void schedule() {
        cancel();
        revision = session.revision();
        if (!started || !enabled.get() || session.connection() == null || session.world() == null)
            return;
        sampleAt = System.nanoTime() + 1_000_000_000L;
        expiresAt = sampleAt + 5_000_000_000L;
        lastAttempt = -1;
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void cancel() {
        MinecraftForge.EVENT_BUS.unregister(this);
        if (network != null) {
            ((PredictionProbeBinding) network).bindPredictionProbe(null);
            network = null;
        }
        probe = null;
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase != Phase.END)
            return;
        if (revision != session.revision()) {
            schedule();
            return;
        }
        long now = System.nanoTime();
        if (probe != null && probe.interrupted) {
            ((PredictionProbeBinding) network).bindPredictionProbe(null);
            probe = null;
        }
        if (probe != null && probe.receivedAt != 0 && probe.receivedAt <= expiresAt) {
            int roundTripMillis =
                    (int) ((probe.receivedAt - probe.sentAt + 49_999_999L) / 50_000_000L) * 50;
            cancel();
            int margin = roundTripMillis / 2;
            setMargin("debufftimers", "venom_hit_window_ms", margin);
            setMargin("debufftimers", "jump_boost_grace_ms", margin);
            setMargin("debufftimers", "pin_hit_window_ms", margin);
            setMargin("telebow", "prediction_margin_ms", margin);
            return;
        }
        if (now >= expiresAt) {
            cancel();
            return;
        }
        if (now < sampleAt)
            return;
        int attempt = (int) ((now - sampleAt) / 1_000_000_000L);
        if (attempt == lastAttempt)
            return;
        lastAttempt = attempt;
        if (network == null) {
            NetHandlerPlayClient connection = session.connection();
            NetworkManager manager = connection.getNetworkManager();
            if (!(manager instanceof PredictionProbeBinding) || !manager.isChannelOpen()) {
                cancel();
                return;
            }
            network = manager;
        }
        if (probe == null) {
            probe = new Probe();
            ((PredictionProbeBinding) network).bindPredictionProbe(probe);
        }
        session.connection().addToSendQueue(probe.request);
    }

    private void setMargin(String featureId, String settingId, int millis) {
        for (Setting<?> setting : configs.feature(featureId).settings()) {
            if (setting.id().equals(settingId)) {
                IntegerSetting margin = (IntegerSetting) setting;
                margin.set(Math.max(margin.minimum(), Math.min(margin.maximum(), millis)));
                return;
            }
        }
    }

    /** Timing state shared by the client and network threads. */
    public static final class Probe {
        private final C16PacketClientStatus request =
                new C16PacketClientStatus(EnumState.REQUEST_STATS);
        private volatile long sentAt;
        private volatile long receivedAt;
        private volatile boolean interrupted;

        public void sending(Packet<?> packet) {
            if (!(packet instanceof C16PacketClientStatus)
                    || ((C16PacketClientStatus) packet).getStatus() != EnumState.REQUEST_STATS)
                return;
            if (packet == request) {
                // A reply can belong to an earlier attempt, so keep the first send time.
                if (sentAt == 0)
                    sentAt = System.nanoTime();
            } else {
                // Statistics replies have no request id, so overlapping requests spoil the sample.
                interrupted = true;
            }
        }

        public void received(Packet<?> packet) {
            if (packet instanceof S37PacketStatistics && sentAt != 0 && receivedAt == 0)
                receivedAt = System.nanoTime();
        }
    }
}
