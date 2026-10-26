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

import static pit12.runtime.languages.Languages.source;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import pit12.feature.hudeditor.api.HudEditor;
import pit12.feature.profile.api.Profiles;
import pit12.runtime.command.CommandNode;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.languages.Languages;
import pit12.shared.event.ClientEvents;
import pit12.shared.lifecycle.ClientLifecycle;

public final class HudEditorFeature implements ClientLifecycle, HudEditor {
    private final Runnable onClientTickListener = this::onClientTick;

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final HudEditorController controller;
    private final HudRegistry registry;
    private final Languages language;
    private IntSupplier interfaceKey;
    private Runnable webUiOpener;
    private BooleanSetting showHint;
    private boolean started;
    private boolean pendingOpen;

    public HudEditorFeature(HudRegistry registry, CommandRegistry commands, Languages language,
            ConfigCatalog configs, Profiles profiles, BooleanSupplier renderHotbar) {
        this.registry = registry;
        this.language = language;
        controller = new HudEditorController(registry, configs, profiles, renderHotbar);
        commands.register(CommandNode.command("hudeditor", source("Open the HUD editor"))
                .executes((sender, args) -> requestOpen()).build(), true);
    }

    public void setWebUiOpener(IntSupplier interfaceKey, Runnable webUiOpener,
            BooleanSetting showHint) {
        this.interfaceKey = interfaceKey;
        this.webUiOpener = webUiOpener;
        this.showHint = showHint;
    }

    @Override
    public void start() {
        registry.checkThread();
        started = true;
    }

    @Override
    public void stop() {
        registry.checkThread();
        if (pendingOpen) {
            ClientEvents.TICK_END.remove(onClientTickListener);
        }
        started = false;
        pendingOpen = false;
        if (minecraft.currentScreen instanceof HudEditorScreen
                && ((HudEditorScreen) minecraft.currentScreen).belongsTo(controller)) {
            minecraft.displayGuiScreen(null);
        }
        controller.close();
    }

    void requestOpen() {
        if (started && !pendingOpen) {
            pendingOpen = true;
            ClientEvents.TICK_END.add(onClientTickListener);
        }
    }

    private void onClientTick() {
        registry.checkThread();
        // GuiChat closes itself after dispatching a command.
        if (!pendingOpen
                || minecraft.currentScreen instanceof GuiChat) {
            return;
        }
        pendingOpen = false;
        ClientEvents.TICK_END.remove(onClientTickListener);
        open();
    }

    @Override
    public void open() {
        registry.checkThread();
        if (!started) {
            return;
        }
        if (minecraft.currentScreen instanceof HudEditorScreen
                && ((HudEditorScreen) minecraft.currentScreen).belongsTo(controller)) {
            return;
        }
        minecraft.displayGuiScreen(new HudEditorScreen(controller, minecraft.currentScreen,
                language, interfaceKey, webUiOpener, showHint));
    }
}
