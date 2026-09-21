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
package pit12.feature.clickgui;

import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class ClickGuiScreen extends GuiScreen {
    private final ClickGuiController controller;
    private float interfaceScale = 1.0F;

    public ClickGuiScreen(ClickGuiController controller) {
        this.controller = controller;
    }

    public boolean belongsTo(ClickGuiController candidate) {
        return controller == candidate;
    }

    @Override
    public void initGui() {
        resizeController();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int logicalMouseX = logical(mouseX);
        int logicalMouseY = logical(mouseY);
        if (Mouse.isButtonDown(0)) {
            controller.mouseDragged(logicalMouseX, logicalMouseY, 0);
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(interfaceScale, interfaceScale, 1.0F);
        try {
            controller.render(logicalMouseX, logicalMouseY, partialTicks);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        controller.mousePressed(logical(mouseX), logical(mouseY), mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton,
            long timeSinceLastClick) {
        controller.mouseDragged(logical(mouseX), logical(mouseY), clickedMouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        controller.mouseReleased(logical(mouseX), logical(mouseY), state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            controller.mouseWheel(logical(mouseX), logical(mouseY), wheel);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (controller.keyTyped(typedChar, keyCode)) {
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
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
        float vapeScale = mc.displayHeight >= 2000 ? 1.5F : mc.displayHeight >= 1000 ? 1.2F : 1.0F;
        interfaceScale = 2.0F * vapeScale / resolution.getScaleFactor();
        float pixelScale = resolution.getScaleFactor() * interfaceScale;
        controller.resize(Math.max(1, (int) Math.floor(width / interfaceScale)),
                Math.max(1, (int) Math.floor(height / interfaceScale)), interfaceScale, pixelScale);
    }

    private int logical(int coordinate) {
        return (int) Math.floor(coordinate / interfaceScale);
    }
}
