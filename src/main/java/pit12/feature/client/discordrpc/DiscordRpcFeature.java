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
package pit12.feature.client.discordrpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.shared.lifecycle.ClientLifecycle;

public final class DiscordRpcFeature implements ClientLifecycle, ConfigChangeListener {
    private final ConfigCatalog configs;
    private final BooleanSetting enabled;
    private final DiscordRpcConnection.Factory connections;
    private boolean started;
    private RpcWorker worker;

    public DiscordRpcFeature(ConfigCatalog configs, BooleanSetting enabled,
            DiscordRpcConnection.Factory connections) {
        this.configs = configs;
        this.enabled = enabled;
        this.connections = connections;
    }

    @Override
    public void start() {
        configs.clientThread().check();
        if (started) {
            return;
        }
        started = true;
        configs.addListener(this);
        worker = new RpcWorker(enabled.get(), connections);
        worker.start();
    }

    @Override
    public void stop() {
        configs.clientThread().check();
        started = false;
        configs.removeListener(this);
        if (worker != null) {
            worker.shutdown();
            worker = null;
        }
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        configs.clientThread().check();
        if (started && changes.affects("webui", "discord_rpc")) {
            worker.setEnabled(enabled.get());
        }
    }

    private static JsonObject createActivity(long startedAt) {
        JsonObject activity = new JsonObject();
        activity.addProperty("type", 0);
        activity.addProperty("details", "A free and open-source pit mod");
        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", startedAt);
        activity.add("timestamps", timestamps);
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "12");
        assets.addProperty("large_text", "12pit");
        activity.add("assets", assets);
        JsonArray buttons = new JsonArray();
        buttons.add(button("GitHub", "https://github.com/12src/12pit"));
        buttons.add(button("Discord", "https://discord.gg/e9PRKMUenc"));
        activity.add("buttons", buttons);
        return activity;
    }

    private static JsonObject button(String label, String url) {
        JsonObject button = new JsonObject();
        button.addProperty("label", label);
        button.addProperty("url", url);
        return button;
    }

    private static final class RpcWorker extends Thread {
        private static final Logger LOGGER = Logger.getLogger(RpcWorker.class.getName());
        private final DiscordRpcConnection.Factory connections;
        // The client thread also uses these during shutdown, so keep them under the monitor.
        private boolean enabled;
        private boolean stopping;
        private DiscordRpcConnection connection;

        private RpcWorker(boolean enabled, DiscordRpcConnection.Factory connections) {
            super("12pit-discord-rpc");
            this.enabled = enabled;
            this.connections = connections;
            setDaemon(true);
        }

        private synchronized void setEnabled(boolean enabled) {
            this.enabled = enabled;
            if (!enabled) {
                closeConnection();
            }
            notifyAll();
        }

        private synchronized void shutdown() {
            stopping = true;
            closeConnection();
            notifyAll();
        }

        private void closeConnection() {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException failure) {
                    LOGGER.log(Level.FINE, "Failed to close Discord RPC connection", failure);
                }
            }
        }

        @Override
        public void run() {
            try {
                long startedAt = ManagementFactory.getRuntimeMXBean().getStartTime() / 1000L;
                while (awaitEnabled()) {
                    try (DiscordRpcConnection opened = connections.open()) {
                        if (opened != null) {
                            synchronized (this) {
                                if (stopping || !enabled) {
                                    continue;
                                }
                                connection = opened;
                            }
                            opened.publish("1553780738573213716", createActivity(startedAt));
                            opened.listen();
                        }
                    } catch (IOException failure) {
                        LOGGER.log(Level.FINE, "Discord RPC connection ended", failure);
                    } finally {
                        synchronized (this) {
                            connection = null;
                        }
                    }
                    synchronized (this) {
                        if (!stopping && enabled) {
                            wait(30000L);
                        }
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.log(Level.WARNING, "Discord RPC stopped after an error", failure);
            }
        }

        private synchronized boolean awaitEnabled() throws InterruptedException {
            while (!stopping && !enabled) {
                wait();
            }
            return !stopping;
        }
    }
}
