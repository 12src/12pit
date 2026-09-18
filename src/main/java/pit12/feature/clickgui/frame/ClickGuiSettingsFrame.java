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
package pit12.feature.clickgui.frame;

import java.util.ArrayList;
import java.util.List;
import pit12.feature.clickgui.ClickGuiConfig;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.ClickGuiState;
import pit12.feature.clickgui.component.ColorPickerComponent;
import pit12.feature.clickgui.component.KeybindComponent;
import pit12.feature.clickgui.component.ToggleComponent;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiRenderer.TextureIcon;
import pit12.feature.clickgui.render.ClickGuiTheme;
import pit12.runtime.config.BooleanSetting;

public final class ClickGuiSettingsFrame extends DraggableFrame {
    private static final int ROW_HEIGHT = 18;
    private static final int BIND_ROW_HEIGHT = 22;
    private final ClickGuiController controller;
    private final KeybindComponent keybind;
    private final ColorPickerComponent colorPicker;
    private final List<OptionRow> options = new ArrayList<OptionRow>();

    public ClickGuiSettingsFrame(ClickGuiController controller, ClickGuiState state,
            ClickGuiConfig config) {
        super("clickgui.settings", "Settings", state, 32, 32, 110, 238);
        this.controller = controller;
        keybind = new KeybindComponent(() -> config.openKeybind().get().intValue(),
                keyCode -> config.openKeybind().set(Integer.valueOf(keyCode)));
        colorPicker = new ColorPickerComponent(config.guiColor().displayName(),
                () -> config.guiColor().get().intValue(),
                color -> config.guiColor().set(Integer.valueOf(color)));
        for (pit12.runtime.config.Setting<?> setting : config.options()) {
            if (setting instanceof BooleanSetting) {
                BooleanSetting booleanSetting = (BooleanSetting) setting;
                options.add(new OptionRow(booleanSetting,
                        new ToggleComponent(0, 0, 0, ROW_HEIGHT, setting.displayName(),
                                () -> booleanSetting.get().booleanValue(), booleanSetting::set)));
            }
        }
        setVisible(false);
    }

    @Override
    protected boolean showCollapseControl() {
        return false;
    }

    @Override
    protected boolean canCollapse() {
        return false;
    }

    @Override
    protected void renderHeaderAction(ClickGuiRenderer renderer, int mouseX, int mouseY) {
        int color = headerActionContains(mouseX, mouseY) ? ClickGuiTheme.TEXT
                : ClickGuiTheme.MUTED_TEXT;
        renderer.centeredTexture(TextureIcon.CLOSE, frameX() + frameWidth() - 12, frameY(),
                HEADER_HEIGHT, 6, 6, color);
    }

    @Override
    protected boolean headerActionContains(int mouseX, int mouseY) {
        return mouseX >= frameX() + frameWidth() - 17 && mouseX < frameX() + frameWidth()
                && mouseY >= frameY() && mouseY < frameY() + HEADER_HEIGHT;
    }

    @Override
    protected boolean headerActionPressed(ClickGuiController controller, int mouseX, int mouseY,
            int button) {
        if (button != 0) {
            return false;
        }
        controller.closeSettings();
        return true;
    }

    @Override
    protected void renderContent(ClickGuiRenderer renderer, int mouseX, int mouseY,
            float partialTicks) {
        int rowY = frameY() + HEADER_HEIGHT + 2;
        for (OptionRow option : options) {
            option.component.setBounds(frameX() + 7, rowY, frameWidth() - 14, ROW_HEIGHT);
            option.component.render(renderer, mouseX, mouseY, partialTicks);
            if (option.component.contains(mouseX, mouseY)
                    && !option.setting.description().isEmpty()) {
                controller.tooltip(option.setting.description(), mouseX, mouseY);
            }
            rowY += ROW_HEIGHT;
        }
        renderer.rect(frameX() + 6, rowY, frameWidth() - 12, 1, ClickGuiTheme.DIVIDER);
        colorPicker.setBounds(frameX(), rowY + 1, frameWidth(), colorPicker.preferredHeight());
        colorPicker.render(renderer, mouseX, mouseY, partialTicks);
        rowY += colorPicker.preferredHeight() + 1;
        boolean bindHovered = inRow(mouseX, mouseY, rowY, BIND_ROW_HEIGHT);
        int bindBackground = bindHovered ? ClickGuiTheme.ROW_HOVER : ClickGuiTheme.ROW;
        renderer.rect(frameX(), rowY, frameWidth(), BIND_ROW_HEIGHT, bindBackground);
        renderer.verticallyCenteredText("Rebind GUI", frameX() + 7, rowY, BIND_ROW_HEIGHT,
                ClickGuiTheme.MUTED_TEXT);
        int bindWidth = keybind.preferredWidth(renderer);
        int bindHeight = keybind.preferredHeight();
        keybind.setBounds(frameX() + frameWidth() - bindWidth - 7,
                rowY + (BIND_ROW_HEIGHT - bindHeight) / 2, bindWidth, bindHeight);
        keybind.setSurfaceColor(bindBackground);
        keybind.render(renderer, mouseX, mouseY, partialTicks);
        if (keybind.contains(mouseX, mouseY)) {
            controller.tooltip(keybind.isCapturing() ? "Remove GUI keybind" : "Change GUI keybind",
                    mouseX, mouseY);
        }
        String version = "12pit";
        renderer.verticallyCenteredText(version,
                frameX() + frameWidth() - renderer.textWidth(version) - 4,
                frameY() + frameHeight() - 15, 12, ClickGuiTheme.DISABLED_TEXT);
    }

    @Override
    protected boolean contentMousePressed(ClickGuiController controller, int mouseX, int mouseY,
            int button) {
        if (button != 0) {
            return false;
        }
        if (colorPicker.contains(mouseX, mouseY)) {
            controller.focus(null);
            return colorPicker.mousePressed(controller, mouseX, mouseY, button);
        }
        if (keybind.contains(mouseX, mouseY)) {
            return keybind.mousePressed(controller, mouseX, mouseY, button);
        }
        controller.focus(null);
        for (OptionRow option : options) {
            if (option.component.contains(mouseX, mouseY)) {
                return option.component.mousePressed(controller, mouseX, mouseY, button);
            }
        }
        return true;
    }

    private boolean inRow(int mouseX, int mouseY, int rowY, int rowHeight) {
        return mouseX >= frameX() && mouseX < frameX() + frameWidth() && mouseY >= rowY
                && mouseY < rowY + rowHeight;
    }

    private static final class OptionRow {
        private final BooleanSetting setting;
        private final ToggleComponent component;

        private OptionRow(BooleanSetting setting, ToggleComponent component) {
            this.setting = setting;
            this.component = component;
        }
    }
}
