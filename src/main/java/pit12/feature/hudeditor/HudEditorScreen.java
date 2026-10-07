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
import pit12.runtime.hud.HudRenderer;
import pit12.runtime.languages.Languages;
import pit12.shared.rendering.UiRenderState;

final class HudEditorScreen extends GuiScreen {
    private final HudEditorController controller;
    private final GuiScreen parent;
    private final Languages language;
    private final HudRenderer renderer = new HudRenderer();
    private final UiRenderState renderState = new UiRenderState();
    private String[] tips;
    private boolean initialized;
    private boolean previousRepeatEvents;

    HudEditorScreen(HudEditorController controller, GuiScreen parent, Languages language) {
        this.controller = controller;
        this.parent = parent;
        this.language = language;
    }

    boolean belongsTo(HudEditorController candidate) {
        return controller == candidate;
    }

    @Override
    public void initGui() {
        if (!initialized) {
            previousRepeatEvents = Keyboard.areRepeatEventsEnabled();
            initialized = true;
        }
        Keyboard.enableRepeatEvents(true);
        controller.open();
        ScaledResolution resolution = new ScaledResolution(mc);
        controller.resize(width, height, resolution.getScaleFactor());
        renderer.resize(resolution.getScaleFactor());
        tips = new String[] {language.translate("Hold Alt to disable snapping."),
                language.translate("Drag to move. Drag empty space to select."),
                language.translate("Ctrl+click to select multiple HUDs."),
                language.translate("Scroll to resize. Hold Shift for smaller steps."),
                language.translate("Arrow keys move by 1 px. Shift moves by 10 px."),
                language.translate("Ctrl+Z to undo. Ctrl+Y to redo."),
                language.translate("R to restore selection. Shift+R to restore all."),
                language.translate("Hold Space to preview live HUDs.")};
    }

    @Override
    public void updateScreen() {
        if (!arrowsDown()) {
            controller.finishNudge();
        }
        updatePreview();
    }

    private void updatePreview() {
        // Normal overlay callbacks own the live display conditions and content.
        controller.preview(Keyboard.isKeyDown(Keyboard.KEY_SPACE));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (Mouse.isButtonDown(0)) {
            controller.mouseDragged(mouseX, mouseY, alt());
        }
        boolean helpHovered = tipsHovered(mouseX, mouseY);
        controller.render(helpHovered ? -1 : mouseX, mouseY, partialTicks);
        if (controller.live()) {
            return;
        }
        renderState.begin();
        try {
            renderer.text("?", width - 20, height - 22, helpHovered ? 0xFFFFFFFF : 0xFFB0B0B0, true,
                    false);
            if (helpHovered) {
                int y = height - 28;
                for (int index = tips.length - 1; index >= 0; index--) {
                    String tip = tips[index];
                    y -= renderer.fontHeight(tip, false) + 2;
                    renderer.text(tip, Math.max(4, width - renderer.textWidth(tip, false) - 8), y,
                            0xFFF0F0F0, true, false);
                }
            }
        } finally {
            renderState.end();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (controller.live() || tipsHovered(mouseX, mouseY)) {
            return;
        }
        if (mouseButton == 0) {
            controller.mousePressed(mouseX, mouseY, isCtrlKeyDown());
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton,
            long timeSinceLastClick) {
        if (clickedMouseButton == 0) {
            controller.mouseDragged(mouseX, mouseY, alt());
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) {
            controller.mouseDragged(mouseX, mouseY, alt());
            controller.mouseReleased();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && !controller.live()) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (!tipsHovered(mouseX, mouseY)) {
                controller.mouseWheel(mouseX, mouseY, wheel, isShiftKeyDown());
            }
        }
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        super.handleKeyboardInput();
        // keyTyped can close this screen before the base handler returns.
        if (mc.currentScreen != this) {
            return;
        }
        if (!arrowsDown()) {
            controller.finishNudge();
        }
        updatePreview();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (!controller.cancelGesture()) {
                mc.displayGuiScreen(parent);
            }
            return;
        }
        if (controller.live() || controller.pointerActive()) {
            return;
        }
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_Z) {
            controller.undo();
        } else if (isCtrlKeyDown() && keyCode == Keyboard.KEY_Y) {
            controller.redo();
        } else if (keyCode == Keyboard.KEY_R && !Keyboard.isRepeatEvent()) {
            controller.reset(isShiftKeyDown());
        } else {
            int step = isShiftKeyDown() ? 10 : 1;
            if (keyCode == Keyboard.KEY_LEFT) {
                controller.nudge(-step, 0);
            } else if (keyCode == Keyboard.KEY_RIGHT) {
                controller.nudge(step, 0);
            } else if (keyCode == Keyboard.KEY_UP) {
                controller.nudge(0, -step);
            } else if (keyCode == Keyboard.KEY_DOWN) {
                controller.nudge(0, step);
            }
        }
    }

    @Override
    public void onGuiClosed() {
        controller.close();
        renderer.close();
        if (initialized) {
            Keyboard.enableRepeatEvents(previousRepeatEvents);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static boolean arrowsDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LEFT) || Keyboard.isKeyDown(Keyboard.KEY_RIGHT)
                || Keyboard.isKeyDown(Keyboard.KEY_UP) || Keyboard.isKeyDown(Keyboard.KEY_DOWN);
    }

    private boolean tipsHovered(int mouseX, int mouseY) {
        return mouseX >= width - 24 && mouseX < width - 8 && mouseY >= height - 24
                && mouseY < height - 8;
    }

    private static boolean alt() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }
}
