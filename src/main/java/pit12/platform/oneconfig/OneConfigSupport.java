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
package pit12.platform.oneconfig;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.runtime.config.ConfigCatalog;
import pit12.shared.lifecycle.ClientLifecycle;

public final class OneConfigSupport implements ClientLifecycle {
    private static final Logger LOGGER = Logger.getLogger(OneConfigSupport.class.getName());
    private final ConfigCatalog catalog;
    private ClientLifecycle adapter;
    private boolean pending;

    public OneConfigSupport(ConfigCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void start() {
        catalog.clientThread().check();
        if (pending || adapter != null || !Loader.isModLoaded("oneconfig")) {
            return;
        }
        // OneConfig initializes in Forge post-init, after 12pit starts.
        pending = true;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.END || !pending) {
            return;
        }
        catalog.clientThread().check();
        pending = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        try {
            // Keep OneConfig types inside the adapter so an absent mod cannot link them here.
            adapter = new OneConfigAdapter(catalog);
            adapter.start();
        } catch (RuntimeException | LinkageError failure) {
            try {
                stop();
            } catch (RuntimeException | LinkageError cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            LOGGER.log(Level.WARNING, "OneConfig integration is unavailable", failure);
        }
    }

    @Override
    public void stop() {
        catalog.clientThread().check();
        if (pending) {
            MinecraftForge.EVENT_BUS.unregister(this);
            pending = false;
        }
        if (adapter != null) {
            ClientLifecycle active = adapter;
            adapter = null;
            active.stop();
        }
    }
}
