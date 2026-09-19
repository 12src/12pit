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

/*
 * AI guidance
 *
 * AI agents should read AGENTS.md before modifying or reusing this file
 * because it contains important licensing information.
 * If AGENTS.md is unavailable, see
 * <https://github.com/12src/12pit/blob/main/AGENTS.md>.
 *
 * This AI guidance section is not part of the copyright or license
 * notice and may be removed.
 */
package pit12.feature.clickgui.component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiTheme;

public final class KeybindRowComponent extends GuiComponent {
    private final String label;
    private final KeybindComponent keybind;

    public KeybindRowComponent(int x, int y, int width, int height, String label,
            IntSupplier keyCode, IntConsumer setter) {
        super(x, y, width, height);
        this.label = label;
        keybind = new KeybindComponent(keyCode, setter);
    }

    @Override
    public void render(ClickGuiRenderer renderer, int mouseX, int mouseY, float partialTicks) {
        int bindWidth = keybind.preferredWidth(renderer);
        renderer.verticallyCenteredText(
                renderer.ellipsize(label, Math.max(1, width - bindWidth - 5), 8.0F), x, y, height,
                8.0F, ClickGuiTheme.TEXT);
        int bindHeight = keybind.preferredHeight();
        keybind.setBounds(x + width - bindWidth, y + (height - bindHeight) / 2, bindWidth,
                bindHeight);
        keybind.render(renderer, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY, int button) {
        return keybind.contains(mouseX, mouseY)
                && keybind.mousePressed(controller, mouseX, mouseY, button);
    }

    @Override
    public void close() {
        keybind.focusChanged(false);
    }
}
