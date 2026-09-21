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
package pit12.feature.clickgui.component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiTheme;
import pit12.runtime.config.NumberSetting;

public final class SliderComponent extends GuiComponent {
    private static final int PREFERRED_HEIGHT = 25;
    private final NumberSetting<?> setting;
    private Number displayedValue;
    private String displayedValueText;
    private boolean dragging;

    public SliderComponent(NumberSetting<?> setting) {
        super(0, 0, 0, PREFERRED_HEIGHT);
        this.setting = setting;
    }

    @Override
    public void render(ClickGuiRenderer renderer, int mouseX, int mouseY, float partialTicks) {
        String valueText = renderer.ellipsize(valueText(), Math.max(1, width), 8.0F);
        int valueWidth = renderer.textWidth(valueText, 8.0F);
        int valueX = Math.max(x, x + width - valueWidth);
        int labelWidth = Math.max(1, valueX - x - 5);
        renderer.verticallyCenteredText(renderer.ellipsize(setting.displayName(), labelWidth, 8.0F),
                x, y, 14, 8.0F, ClickGuiTheme.MUTED_TEXT);
        renderer.verticallyCenteredText(valueText, valueX, y, 14, 8.0F, ClickGuiTheme.TEXT);
        int trackY = y + 19;
        double progress = Math.max(0.0D, Math.min(1.0D, setting.fraction()));
        int handleX = x + (int) Math.round(progress * Math.max(0, width - 1));
        renderer.rect(x, trackY, width, 2, ClickGuiTheme.BORDER);
        renderer.rect(x, trackY, Math.max(0, handleX - x + 1), 2, ClickGuiTheme.ACCENT_DARK);
        renderer.roundedRect(handleX - 2, trackY - 3, 5, 8, 2, ClickGuiTheme.ACCENT);
    }

    @Override
    public boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY, int button) {
        if (button != 0 || !isEnabled() || !contains(mouseX, mouseY) || mouseY < y + 13) {
            return false;
        }
        updateValue(mouseX);
        dragging = true;
        controller.capture(this);
        return true;
    }

    @Override
    public void mouseDragged(ClickGuiController controller, int mouseX, int mouseY, int button) {
        if (dragging) {
            updateValue(mouseX);
        }
    }

    @Override
    public void mouseReleased(ClickGuiController controller, int mouseX, int mouseY, int button) {
        if (!dragging) {
            return;
        }
        dragging = false;
        controller.releaseCapture(this);
    }

    private void updateValue(int mouseX) {
        double position = (mouseX - x) / (double) Math.max(1, width - 1);
        setting.setFromFraction(position);
    }

    private String valueText() {
        Number value = setting.get();
        if (!value.equals(displayedValue)) {
            displayedValue = value;
            displayedValueText = BigDecimal.valueOf(value.doubleValue())
                    .setScale(setting.decimalPlaces(), RoundingMode.HALF_UP).toPlainString();
        }
        return displayedValueText;
    }
}
