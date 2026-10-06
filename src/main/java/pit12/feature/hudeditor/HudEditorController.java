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

import java.util.List;
import pit12.runtime.config.HudPlacement;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.hud.HudBounds;
import pit12.runtime.hud.HudElement;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.hud.HudRenderer;
import pit12.shared.rendering.UiRenderState;

final class HudEditorController {
    private final HudRegistry registry;
    private final HudRenderer renderer = new HudRenderer();
    private final UiRenderState renderState = new UiRenderState();
    private HudElement selected;
    private HudElement dragging;
    private int screenWidth;
    private int screenHeight;
    private float pixelScale = 1.0F;
    private int dragX;
    private int dragY;
    private int dragOffsetX;
    private int dragOffsetY;

    HudEditorController(HudRegistry registry) {
        this.registry = registry;
    }

    void resize(int screenWidth, int screenHeight, float pixelScale) {
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
        this.pixelScale = Math.max(0.01F, pixelScale);
        renderer.resize(pixelScale);
    }

    void open() {
        registry.setEditing(true);
    }

    void close() {
        dragging = null;
        registry.setEditing(false);
    }

    void render(int mouseX, int mouseY, float partialTicks) {
        if (!registry.contains(selected)) {
            selected = null;
        }
        if (!registry.contains(dragging)) {
            dragging = null;
        }
        renderState.begin();
        try {
            renderer.rect(0, 0, screenWidth, screenHeight, 0x26000000);
            for (HudElement element : registry.elements()) {
                if (element != selected) {
                    renderElement(element, mouseX, mouseY, partialTicks);
                }
            }
            if (selected != null) {
                renderElement(selected, mouseX, mouseY, partialTicks);
            }
        } finally {
            renderState.end();
        }
    }

    void mousePressed(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (selected != null && beginDrag(selected, mouseX, mouseY)) {
            return;
        }
        List<HudElement> elements = registry.elements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            HudElement element = elements.get(index);
            if (element != selected && beginDrag(element, mouseX, mouseY)) {
                return;
            }
        }
        selected = null;
    }

    void mouseDragged(int mouseX, int mouseY) {
        if (dragging == null) {
            return;
        }
        HudBounds bounds =
                HudRenderer.layout(dragging, screenWidth, screenHeight, pixelScale, true);
        int width = bounds.width;
        int height = bounds.height;
        dragX = snap(clamp(mouseX - dragOffsetX, 0, Math.max(0, screenWidth - width)), width,
                screenWidth);
        dragY = snap(clamp(mouseY - dragOffsetY, 0, Math.max(0, screenHeight - height)), height,
                screenHeight);
    }

    void mouseReleased(int button) {
        if (button != 0 || dragging == null) {
            return;
        }
        if (registry.contains(dragging)) {
            HudBounds bounds =
                    HudRenderer.layout(dragging, screenWidth, screenHeight, pixelScale, true);
            int width = bounds.width;
            int height = bounds.height;
            dragging.config().placement(HudPlacement.fromOrigin(dragX, dragY, width, height,
                    screenWidth, screenHeight));
        }
        dragging = null;
    }

    void mouseWheel(int delta) {
        // Changing scale during a drag invalidates the stored offsets.
        if (selected == null || dragging != null) {
            return;
        }
        IntegerSetting scale = selected.config().scale();
        int next = scale.get() + Integer.signum(delta) * 5;
        scale.set(clamp(next, scale.minimum(), scale.maximum()));
    }

    void dispose() {
        close();
        selected = null;
        renderer.close();
    }

    private boolean beginDrag(HudElement element, int mouseX, int mouseY) {
        HudBounds bounds = HudRenderer.layout(element, screenWidth, screenHeight, pixelScale, true);
        if (element == dragging)
            bounds = bounds.at(dragX, dragY);
        int width = bounds.width;
        int height = bounds.height;
        int x = bounds.x;
        int y = bounds.y;
        if (!contains(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        selected = element;
        dragging = element;
        dragX = x;
        dragY = y;
        dragOffsetX = mouseX - x;
        dragOffsetY = mouseY - y;
        return true;
    }

    private void renderElement(HudElement element, int mouseX, int mouseY, float partialTicks) {
        HudBounds bounds = HudRenderer.layout(element, screenWidth, screenHeight, pixelScale, true);
        if (element == dragging)
            bounds = bounds.at(dragX, dragY);
        int width = bounds.width;
        int height = bounds.height;
        int x = bounds.x;
        int y = bounds.y;
        renderer.render(element, bounds, partialTicks, true);
        boolean hovered = contains(mouseX, mouseY, x, y, width, height);
        int color = element == selected ? 0xFF26CEAA : hovered ? 0xFFD1D1D1 : 0x80909090;
        outline(x, y, width, height, color);
        if (element == selected || hovered) {
            String label = element.displayName() + "  " + element.config().scale().get() + "%";
            int labelX =
                    clamp(x, 1, Math.max(1, screenWidth - renderer.textWidth(label, false) - 1));
            int fontHeight = renderer.fontHeight(label, false);
            int labelY = clamp(y >= fontHeight + 3 ? y - fontHeight - 2 : y + height + 2, 1,
                    Math.max(1, screenHeight - fontHeight - 1));
            renderer.text(label, labelX, labelY, 0xFFF0F0F0, true, false);
        }
    }

    private void outline(int x, int y, int width, int height, int color) {
        renderer.rect(x, y, width, 1, color);
        renderer.rect(x, y + height - 1, width, 1, color);
        renderer.rect(x, y, 1, height, color);
        renderer.rect(x + width - 1, y, 1, height, color);
    }

    private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int snap(int value, int elementLength, int screenLength) {
        int maximum = Math.max(0, screenLength - elementLength);
        int center = maximum / 2;
        if (Math.abs(value) <= 4) {
            return 0;
        }
        if (Math.abs(value - center) <= 4) {
            return center;
        }
        return Math.abs(value - maximum) <= 4 ? maximum : value;
    }
}
