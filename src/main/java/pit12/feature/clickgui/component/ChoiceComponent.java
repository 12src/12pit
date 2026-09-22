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

import java.util.List;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiRenderer.TextureIcon;
import pit12.feature.clickgui.render.ClickGuiTheme;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ChoiceSetting.Choice;

public final class ChoiceComponent extends GuiComponent {
    private static final int COLLAPSED_HEIGHT = 34;
    private static final int FIELD_Y = 14;
    private static final int FIELD_HEIGHT = 16;
    private static final int CHOICE_HEIGHT = 17;
    private final ChoiceSetting setting;
    private boolean expanded;

    public ChoiceComponent(ChoiceSetting setting) {
        super(0, 0, 0, COLLAPSED_HEIGHT);
        this.setting = setting;
    }

    @Override
    public int preferredHeight() {
        return COLLAPSED_HEIGHT + (expanded ? setting.choices().size() * CHOICE_HEIGHT : 0);
    }

    @Override
    public int maximumHeight() {
        return COLLAPSED_HEIGHT + setting.choices().size() * CHOICE_HEIGHT;
    }

    @Override
    public void render(ClickGuiRenderer renderer, int mouseX, int mouseY, float partialTicks) {
        renderer.verticallyCenteredText(
                renderer.ellipsize(setting.displayName(), Math.max(1, width), 8.0F), x, y,
                FIELD_Y, 8.0F, ClickGuiTheme.MUTED_TEXT);
        int fieldY = y + FIELD_Y;
        renderer.roundedRect(x, fieldY, width, FIELD_HEIGHT, 3, ClickGuiTheme.PANEL_INSET);
        renderer.roundedOutline(x - 0.5F, fieldY - 0.5F, width + 1.0F, FIELD_HEIGHT + 1.0F,
                3.1F, 1.0F, ClickGuiTheme.BORDER);
        String selected = setting.selectedChoice().displayName();
        renderer.verticallyCenteredText(renderer.ellipsize(selected, Math.max(1, width - 19), 8.0F),
                x + 5, fieldY, FIELD_HEIGHT, 8.0F, ClickGuiTheme.TEXT);
        renderer.centeredTexture(TextureIcon.SELECTOR, x + width - 13, fieldY, FIELD_HEIGHT, 7, 7,
                ClickGuiTheme.MUTED_TEXT);
        if (!expanded) {
            return;
        }
        List<Choice> choices = setting.choices();
        for (int index = 0; index < choices.size(); index++) {
            Choice choice = choices.get(index);
            int choiceY = y + COLLAPSED_HEIGHT + index * CHOICE_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= choiceY
                    && mouseY < choiceY + CHOICE_HEIGHT;
            boolean selectedChoice = choice.value() == setting.get().intValue();
            renderer.roundedRect(x, choiceY, width, CHOICE_HEIGHT - 1, 3,
                    selectedChoice ? ClickGuiTheme.ROW_PRESSED
                            : hovered ? ClickGuiTheme.ROW_HOVER : ClickGuiTheme.PANEL_INSET);
            renderer.verticallyCenteredText(
                    renderer.ellipsize(choice.displayName(), Math.max(1, width - 10), 8.0F), x + 5,
                    choiceY, CHOICE_HEIGHT - 1, 8.0F,
                    selectedChoice ? ClickGuiTheme.ACCENT : ClickGuiTheme.TEXT);
        }
    }

    @Override
    public boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY, int button) {
        if (button != 0 || !isEnabled() || !contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseY >= y + FIELD_Y && mouseY < y + FIELD_Y + FIELD_HEIGHT) {
            expanded = !expanded;
            height = preferredHeight();
            return true;
        }
        if (expanded && mouseY >= y + COLLAPSED_HEIGHT) {
            int index = (mouseY - y - COLLAPSED_HEIGHT) / CHOICE_HEIGHT;
            List<Choice> choices = setting.choices();
            if (index < choices.size()) {
                setting.set(Integer.valueOf(choices.get(index).value()));
                expanded = false;
                height = preferredHeight();
            }
        }
        return true;
    }
}
