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
package pit12.feature.clickgui.component;

import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiRenderer.TextureIcon;
import pit12.feature.clickgui.render.ClickGuiTheme;

public class ButtonComponent extends GuiComponent {
    private String label;
    private final Runnable action;
    private int normalColor = ClickGuiTheme.BUTTON;
    private int hoverColor = ClickGuiTheme.BUTTON_HOVER;
    private int textColor = ClickGuiTheme.TEXT;
    private final UiAnimation hoverAnimation = new UiAnimation();
    private TextureIcon textureIcon;
    private boolean accentColors;

    public ButtonComponent(int x, int y, int width, int height, String label, Runnable action) {
        super(x, y, width, height);
        this.label = label;
        this.action = action;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setColors(int normalColor, int hoverColor, int textColor) {
        accentColors = false;
        this.normalColor = normalColor;
        this.hoverColor = hoverColor;
        this.textColor = textColor;
    }

    public void setAccentColors() {
        accentColors = true;
    }

    public void setTextureIcon(TextureIcon textureIcon) {
        this.textureIcon = textureIcon;
    }

    @Override
    public void render(ClickGuiRenderer renderer, int mouseX, int mouseY, float partialTicks) {
        float hovered = hoverAnimation.update(contains(mouseX, mouseY) && isEnabled(),
                renderer.animationsEnabled());
        int color = renderer.mix(accentColors ? ClickGuiTheme.ACCENT_DARK : normalColor,
                accentColors ? ClickGuiTheme.ACCENT : hoverColor, hovered);
        renderer.roundedRect(x, y, width, height, 4, color);
        int resolvedTextColor = isEnabled() ? accentColors ? ClickGuiTheme.ACCENT_TEXT : textColor
                : ClickGuiTheme.DISABLED_TEXT;
        if (textureIcon == null) {
            renderer.centeredText(label, x, y, width, height, 8.0F, resolvedTextColor);
        } else {
            int contentWidth = 8 + renderer.textWidth(label, 8.0F) + 3;
            int contentX = x + (width - contentWidth) / 2;
            float iconX = label.isEmpty() ? x + (width - 7) / 2.0F : contentX;
            renderer.centeredTexture(textureIcon, iconX, y, height, 7, 7, resolvedTextColor);
            if (!label.isEmpty()) {
                renderer.verticallyCenteredText(label, contentX + 11, y, height, 8.0F,
                        resolvedTextColor);
            }
        }
    }

    @Override
    public boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY, int button) {
        if (button != 0 || !isEnabled() || !contains(mouseX, mouseY)) {
            return false;
        }
        action.run();
        return true;
    }
}
