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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.render.ClickGuiRenderer;

public class ContainerComponent extends GuiComponent {
    private final List<GuiComponent> children = new ArrayList<GuiComponent>();

    public ContainerComponent(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public final void add(GuiComponent child) {
        children.add(child);
    }

    public final void clear() {
        for (GuiComponent child : children) {
            child.close();
        }
        children.clear();
    }

    public final List<GuiComponent> children() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void render(ClickGuiRenderer renderer, int mouseX, int mouseY, float partialTicks) {
        for (GuiComponent child : children) {
            if (child.isVisible()) {
                child.render(renderer, mouseX, mouseY, partialTicks);
            }
        }
    }

    @Override
    public boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY, int button) {
        for (int index = children.size() - 1; index >= 0; index--) {
            GuiComponent child = children.get(index);
            if (child.isVisible() && child.contains(mouseX, mouseY)
                    && child.mousePressed(controller, mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseWheel(ClickGuiController controller, int mouseX, int mouseY, int delta) {
        for (int index = children.size() - 1; index >= 0; index--) {
            GuiComponent child = children.get(index);
            if (child.isVisible() && child.contains(mouseX, mouseY)
                    && child.mouseWheel(controller, mouseX, mouseY, delta)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        clear();
    }
}
