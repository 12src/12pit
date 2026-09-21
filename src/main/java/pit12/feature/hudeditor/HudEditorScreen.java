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

import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

final class HudEditorScreen extends GuiScreen {
    private final HudEditorController controller;
    private final GuiScreen parent;

    HudEditorScreen(HudEditorController controller, GuiScreen parent) {
        this.controller = controller;
        this.parent = parent;
    }

    boolean belongsTo(HudEditorController candidate) {
        return controller == candidate;
    }

    @Override
    public void initGui() {
        controller.open();
        resizeController();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (Mouse.isButtonDown(0)) {
            controller.mouseDragged(mouseX, mouseY);
        }
        controller.render(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        controller.mousePressed(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton,
            long timeSinceLastClick) {
        controller.mouseDragged(mouseX, mouseY);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        controller.mouseReleased(state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            controller.mouseWheel(wheel);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void onGuiClosed() {
        controller.close();
    }

    @Override
    public void onResize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        super.onResize(minecraft, width, height);
        resizeController();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void resizeController() {
        ScaledResolution resolution = new ScaledResolution(mc);
        controller.resize(width, height, resolution.getScaleFactor());
    }
}
