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
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.hud.HudRenderer;
import pit12.runtime.languages.Languages;
import pit12.shared.rendering.UiRenderState;

final class HudEditorScreen extends GuiScreen {
    private final HudEditorController controller;
    private final GuiScreen parent;
    private final Languages language;
    private final IntSupplier interfaceKey;
    private final Runnable webUiOpener;
    private final BooleanSetting showHint;
    private final HudRenderer renderer = new HudRenderer();
    private final UiRenderState renderState = new UiRenderState();
    private String[] tips;
    private String[] interfaceTips;
    private GuiButton continueButton;
    private GuiButton hideHintButton;
    private boolean hintVisible;
    private int hintWidth;
    private boolean initialized;
    private boolean previousRepeatEvents;

    HudEditorScreen(HudEditorController controller, GuiScreen parent, Languages language,
            IntSupplier interfaceKey, Runnable webUiOpener, BooleanSetting showHint) {
        this.controller = controller;
        this.parent = parent;
        this.language = language;
        this.interfaceKey = interfaceKey;
        this.webUiOpener = webUiOpener;
        this.showHint = showHint;
    }

    boolean belongsTo(HudEditorController candidate) {
        return controller == candidate;
    }

    @Override
    public void initGui() {
        if (!initialized) {
            previousRepeatEvents = Keyboard.areRepeatEventsEnabled();
            hintVisible = showHint.get() && interfaceKey.getAsInt() != Keyboard.KEY_NONE;
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
        if (interfaceKey.getAsInt() != Keyboard.KEY_NONE) {
            interfaceTips = new String[] {
                    language.format("When no HUD is selected, press {0} again to open the Web UI.",
                            Keyboard.getKeyName(interfaceKey.getAsInt()))};
        } else {
            interfaceTips = new String[0];
            hintVisible = false;
        }
        hintWidth = Math.min(width - 16, 360);
        int buttonWidth = (hintWidth - 32) / 2;
        int buttonY = (height - 90) / 2 + 58;
        continueButton = new GuiButton(0, (width - hintWidth) / 2 + 12, buttonY, buttonWidth, 20,
                language.translate("Got it"));
        hideHintButton = new GuiButton(1, width / 2 + 4, buttonY, buttonWidth, 20,
                language.translate("Don't show again"));
        continueButton.visible = hintVisible;
        hideHintButton.visible = hintVisible;
        buttonList.add(continueButton);
        buttonList.add(hideHintButton);
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
        controller.preview(!hintVisible && Keyboard.isKeyDown(Keyboard.KEY_SPACE)
                && (interfaceKey.getAsInt() != Keyboard.KEY_SPACE || controller.hasSelection()
                        || controller.busy()));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!hintVisible && Mouse.isButtonDown(0)) {
            controller.mouseDragged(mouseX, mouseY, alt());
        }
        boolean helpHovered = !hintVisible && tipsHovered(mouseX, mouseY);
        controller.render(hintVisible || helpHovered ? -1 : mouseX, mouseY, partialTicks);
        if (controller.live()) {
            return;
        }
        renderState.begin();
        try {
            if (hintVisible) {
                int top = (height - 90) / 2;
                renderer.rect(0, 0, width, height, 0x70000000);
                renderer.rect((width - hintWidth) / 2, top, hintWidth, 90, 0xF016191D);
                String text = interfaceTips[0];
                int textWidth = renderer.textWidth(text, false);
                float scale = Math.min(1.0F, (hintWidth - 24.0F) / textWidth);
                GlStateManager.translate(width / 2.0F, top + 24.0F, 0.0F);
                GlStateManager.scale(scale, scale, 1.0F);
                renderer.text(text, -textWidth / 2, 0, 0xFFE0E0E0, true, false);
            } else {
                renderer.text("?", width - 20, height - 22, helpHovered ? 0xFFFFFFFF : 0xFFB0B0B0,
                        true, false);
                if (helpHovered) {
                    drawHelp(tips, drawHelp(interfaceTips, height - 28));
                }
            }
        } finally {
            renderState.end();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == hideHintButton) {
            showHint.set(false);
        }
        dismissHint();
    }

    private void dismissHint() {
        hintVisible = false;
        continueButton.visible = false;
        hideHintButton.visible = false;
    }

    private int drawHelp(String[] lines, int y) {
        for (int index = lines.length - 1; index >= 0; index--) {
            String tip = lines[index];
            y -= renderer.fontHeight(tip, false) + 2;
            renderer.text(tip, Math.max(4, width - renderer.textWidth(tip, false) - 8), y,
                    0xFFF0F0F0, true, false);
        }
        return y;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (hintVisible) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
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
        if (!hintVisible && clickedMouseButton == 0) {
            controller.mouseDragged(mouseX, mouseY, alt());
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (!hintVisible && state == 0) {
            controller.mouseDragged(mouseX, mouseY, alt());
            controller.mouseReleased();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && !hintVisible && !controller.live()) {
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
        if (hintVisible && keyCode != interfaceKey.getAsInt()) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN
                    || keyCode == Keyboard.KEY_NUMPADENTER) {
                dismissHint();
            }
            return;
        }
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
        } else if (keyCode == Keyboard.KEY_R && (controller.hasSelection() || isShiftKeyDown())
                && !Keyboard.isRepeatEvent()) {
            controller.reset(isShiftKeyDown());
        } else if (!controller.hasSelection() && !controller.busy() && keyCode != Keyboard.KEY_NONE
                && keyCode == interfaceKey.getAsInt() && !Keyboard.isRepeatEvent()) {
            mc.displayGuiScreen(parent);
            webUiOpener.run();
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
