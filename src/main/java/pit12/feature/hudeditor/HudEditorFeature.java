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
package pit12.feature.hudeditor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.Feature;
import pit12.feature.hudeditor.api.HudEditor;
import pit12.runtime.hud.HudRegistry;

public final class HudEditorFeature implements Feature, HudEditor {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final HudEditorController controller;
    private boolean started;
    private boolean registered;
    private boolean pendingOpen;

    public HudEditorFeature(HudRegistry registry) {
        controller = new HudEditorController(registry);
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        MinecraftForge.EVENT_BUS.register(this);
        if (!registered) {
            // Forge's client command registry has no matching unregister operation.
            ClientCommandHandler.instance.registerCommand(new HudEditorCommand(this));
            registered = true;
        }
    }

    @Override
    public void stop() {
        if (started) {
            MinecraftForge.EVENT_BUS.unregister(this);
        }
        started = false;
        pendingOpen = false;
        if (minecraft.currentScreen instanceof HudEditorScreen
                && ((HudEditorScreen) minecraft.currentScreen).belongsTo(controller)) {
            minecraft.displayGuiScreen(null);
        }
        controller.dispose();
    }

    void requestOpen() {
        if (started) {
            pendingOpen = true;
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        // GuiChat closes itself after dispatching a command, so wait until it is gone.
        if (event.phase != Phase.END || !pendingOpen
                || minecraft.currentScreen instanceof GuiChat) {
            return;
        }
        pendingOpen = false;
        open();
    }

    @Override
    public void open() {
        if (!started) {
            return;
        }
        if (minecraft.currentScreen instanceof HudEditorScreen
                && ((HudEditorScreen) minecraft.currentScreen).belongsTo(controller)) {
            return;
        }
        minecraft.displayGuiScreen(new HudEditorScreen(controller, minecraft.currentScreen));
    }
}
