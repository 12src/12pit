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

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiTheme;

public final class ToggleComponent extends GuiComponent {
    private final String label;
    private final BooleanSupplier value;
    private final Consumer<Boolean> setter;
    private final UiAnimation stateAnimation = new UiAnimation();

    public ToggleComponent(int x, int y, int width, int height, String label, BooleanSupplier value,
            Consumer<Boolean> setter) {
        super(x, y, width, height);
        this.label = label;
        this.value = value;
        this.setter = setter;
    }

    @Override
    public void render(ClickGuiRenderer renderer, int mouseX, int mouseY, float partialTicks) {
        int toggleWidth = 14;
        int toggleHeight = 8;
        int toggleX = x + width - toggleWidth;
        int toggleY = y + (height - toggleHeight) / 2;
        renderer.verticallyCenteredText(
                renderer.ellipsize(label, Math.max(1, width - toggleWidth - 5), 8.0F), x, y, height,
                8.0F, ClickGuiTheme.TEXT);
        boolean enabled = value.getAsBoolean();
        float progress = stateAnimation.update(enabled, renderer.animationsEnabled());
        renderer.roundedRect(toggleX, toggleY, toggleWidth, toggleHeight, 3,
                renderer.mix(ClickGuiTheme.ROW_PRESSED, ClickGuiTheme.ACCENT_DARK, progress));
        renderer.roundedOutline(toggleX, toggleY, toggleWidth, toggleHeight, 3.0F, 0.75F,
                renderer.mix(ClickGuiTheme.BORDER, ClickGuiTheme.ACCENT, progress));
        int knobX = toggleX + 2 + Math.round(6.0F * progress);
        renderer.roundedRect(knobX, toggleY + 2, 4, 4, 1,
                renderer.mix(ClickGuiTheme.TEXT, ClickGuiTheme.ACCENT_TEXT, progress));
    }

    @Override
    public boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY, int button) {
        if (button != 0 || !isEnabled() || !contains(mouseX, mouseY)) {
            return false;
        }
        setter.accept(Boolean.valueOf(!value.getAsBoolean()));
        return true;
    }
}
