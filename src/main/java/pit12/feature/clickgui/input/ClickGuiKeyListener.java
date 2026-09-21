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
package pit12.feature.clickgui.input;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import pit12.feature.clickgui.ClickGuiConfig;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.ClickGuiScreen;

public final class ClickGuiKeyListener {
    private final Minecraft minecraft;
    private final ClickGuiController controller;
    private final ClickGuiConfig config;

    public ClickGuiKeyListener(Minecraft minecraft, ClickGuiController controller,
            ClickGuiConfig config) {
        this.minecraft = minecraft;
        this.controller = controller;
        this.config = config;
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent ignoredEvent) {
        int configuredKey = config.openKeybind().get().intValue();
        if (configuredKey == Keyboard.KEY_NONE || !Keyboard.getEventKeyState()
                || Keyboard.isRepeatEvent() || Keyboard.getEventKey() != configuredKey) {
            return;
        }
        if (minecraft.currentScreen == null) {
            minecraft.displayGuiScreen(new ClickGuiScreen(controller));
        } else if (minecraft.currentScreen instanceof ClickGuiScreen
                && ((ClickGuiScreen) minecraft.currentScreen).belongsTo(controller)) {
            if (controller.hasFocusedInput()) {
                return;
            }
            minecraft.displayGuiScreen(null);
        }
    }
}
