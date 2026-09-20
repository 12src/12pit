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
import pit12.feature.clickgui.component.OptionComponent;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiRenderer.TextureIcon;
import pit12.feature.clickgui.render.ClickGuiTheme;
import pit12.runtime.config.ConfigOption;

public final class ClickGuiSettingsFrame extends DraggableFrame {
    private static final int FOOTER_HEIGHT = 22;
    private final ClickGuiController controller;
    private final List<OptionComponent> options = new ArrayList<OptionComponent>();

    public ClickGuiSettingsFrame(ClickGuiController controller, ClickGuiState state,
            ClickGuiConfig config) {
        super("clickgui.settings", "Settings", state, 32, 32, 110, 238);
        this.controller = controller;
        for (ConfigOption<?> option : config.options()) {
            options.add(new OptionComponent(option));
        }
        int optionsHeight = 0;
        for (OptionComponent option : options) {
            optionsHeight += option.maximumHeight();
        }
        setFrameHeight(HEADER_HEIGHT + 2 + optionsHeight + FOOTER_HEIGHT);
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
        for (OptionComponent option : options) {
            int optionHeight = option.preferredHeight();
            boolean hovered = inRow(mouseX, mouseY, rowY, optionHeight);
            renderer.rect(frameX(), rowY, frameWidth(), optionHeight,
                    hovered ? ClickGuiTheme.ROW_HOVER : ClickGuiTheme.PANEL);
            option.setBounds(frameX() + 7, rowY, frameWidth() - 14);
            option.render(renderer, mouseX, mouseY, partialTicks);
            if (hovered && !option.setting().description().isEmpty()) {
                controller.tooltip(option.setting().description(), mouseX, mouseY);
            }
            rowY += optionHeight;
        }
        String version = "12pit";
        renderer.verticallyCenteredText(version,
                frameX() + frameWidth() - renderer.textWidth(version, 8.0F) - 4,
                frameY() + frameHeight() - 15, 12, 8.0F, ClickGuiTheme.DISABLED_TEXT);
    }

    @Override
    protected boolean contentMousePressed(ClickGuiController controller, int mouseX, int mouseY,
            int button) {
        if (button != 0) {
            return false;
        }
        for (OptionComponent option : options) {
            if (option.contains(mouseX, mouseY)) {
                option.mousePressed(controller, mouseX, mouseY, button);
                return true;
            }
        }
        return true;
    }

    private boolean inRow(int mouseX, int mouseY, int rowY, int rowHeight) {
        return mouseX >= frameX() && mouseX < frameX() + frameWidth() && mouseY >= rowY
                && mouseY < rowY + rowHeight;
    }
}
