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
package pit12.feature.webui;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import pit12.feature.Feature;
import pit12.feature.profile.api.Profiles;
import pit12.feature.relation.api.Relations;
import pit12.runtime.config.ConfigCatalog;

public final class WebUiFeature implements Feature {
    private static final Logger LOGGER = Logger.getLogger(WebUiFeature.class.getName());
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final WebUiConfig config;
    private final WebUiServer server;
    private boolean started;

    public WebUiFeature(ConfigCatalog catalog, Profiles profiles, Relations relations,
            WebUiConfig config) {
        this.config = config;
        server = new WebUiServer(minecraft, catalog, profiles, relations);
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        try {
            server.start();
        } catch (IOException failure) {
            LOGGER.log(Level.WARNING, "Web UI is unavailable", failure);
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        started = true;
        LOGGER.info("Web UI available at " + server.address());
    }

    @Override
    public void stop() {
        if (started) {
            MinecraftForge.EVENT_BUS.unregister(this);
            started = false;
        }
        server.stop();
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent ignoredEvent) {
        int key = config.keybind().get().intValue();
        if (key == Keyboard.KEY_NONE || minecraft.currentScreen != null
                || !Keyboard.getEventKeyState() || Keyboard.isRepeatEvent()
                || Keyboard.getEventKey() != key) {
            return;
        }
        URI address = server.address();
        Thread opener = new Thread(() -> {
            try {
                if (!Desktop.isDesktopSupported()
                        || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new IOException("Desktop browser is unavailable");
                }
                Desktop.getDesktop().browse(address);
            } catch (IOException | RuntimeException failure) {
                LOGGER.log(Level.WARNING, "Cannot open settings page at " + address, failure);
            }
        }, "12pit-web-open");
        opener.setDaemon(true);
        opener.start();
    }
}
