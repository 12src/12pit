/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 12dev <https://github.com/12src>
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
package pit12.feature.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import pit12.feature.Feature;
import pit12.feature.clickgui.input.ClickGuiKeyListener;
import pit12.feature.profile.api.Profiles;
import pit12.runtime.config.ConfigCatalog;

public final class ClickGuiFeature implements Feature {
    private final Minecraft minecraft;
    private final ClickGuiController controller;
    private final ClickGuiKeyListener keyListener;
    private boolean started;

    public ClickGuiFeature(ConfigCatalog catalog, Profiles profiles, ClickGuiConfig config) {
        minecraft = Minecraft.getMinecraft();
        controller = new ClickGuiController(catalog, profiles, config);
        keyListener = new ClickGuiKeyListener(minecraft, controller, config);
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(keyListener);
        started = true;
    }

    @Override
    public void stop() {
        if (!started) {
            controller.dispose();
            return;
        }
        started = false;
        MinecraftForge.EVENT_BUS.unregister(keyListener);
        if (minecraft.currentScreen instanceof ClickGuiScreen
                && ((ClickGuiScreen) minecraft.currentScreen).belongsTo(controller)) {
            minecraft.displayGuiScreen(null);
        } else {
            controller.close();
        }
        controller.dispose();
    }
}
