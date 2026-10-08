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
package pit12.feature.discordrpc;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
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
    private boolean started;
    private RpcWorker worker;

    public DiscordRpcFeature(ConfigCatalog configs, BooleanSetting enabled) {
        this.configs = configs;
        this.enabled = enabled;
    }

    @Override
    public void start() {
        configs.clientThread().check();
        if (started) {
            return;
        }
        started = true;
        configs.addListener(this);
        worker = new RpcWorker(enabled.get());
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

    private static final class RpcWorker extends Thread {
        private static final Logger LOGGER = Logger.getLogger(RpcWorker.class.getName());
        // The monitor protects settings and the connection from client-thread shutdown.
        private boolean enabled;
        private boolean stopping;
        private DiscordRpcConnection connection;

        private RpcWorker(boolean enabled) {
            super("12pit-discord-rpc");
            this.enabled = enabled;
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
                RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
                // HotSpot exposes the Minecraft process ID as pid@hostname on Java 8.
                String process = runtime.getName();
                int pid = Integer.parseInt(process.substring(0, process.indexOf('@')));
                long startedAt = runtime.getStartTime() / 1000L;
                while (awaitEnabled()) {
                    try (DiscordRpcConnection opened = DiscordRpcConnection.open()) {
                        if (opened != null) {
                            synchronized (this) {
                                if (stopping || !enabled) {
                                    continue;
                                }
                                connection = opened;
                            }
                            opened.publish(pid, startedAt);
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
