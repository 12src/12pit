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

import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.render.ClickGuiRenderer;

public abstract class GuiComponent {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    private boolean visible = true;
    private boolean enabled = true;

    protected GuiComponent(int x, int y, int width, int height) {
        setBounds(x, y, width, height);
    }

    public final void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public final boolean contains(int mouseX, int mouseY) {
        return visible && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public final boolean isVisible() {
        return visible;
    }

    public final void setVisible(boolean visible) {
        this.visible = visible;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public abstract void render(ClickGuiRenderer renderer, int mouseX, int mouseY,
            float partialTicks);

    public boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY, int button) {
        return false;
    }

    public void mouseDragged(ClickGuiController controller, int mouseX, int mouseY, int button) {}

    public void mouseReleased(ClickGuiController controller, int mouseX, int mouseY, int button) {}

    public boolean mouseWheel(ClickGuiController controller, int mouseX, int mouseY, int delta) {
        return false;
    }

    public boolean keyTyped(ClickGuiController controller, char character, int keyCode) {
        return false;
    }

    public void focusChanged(boolean focused) {}

    public GuiComponent initialFocus() {
        return null;
    }

    public void close() {}
}
