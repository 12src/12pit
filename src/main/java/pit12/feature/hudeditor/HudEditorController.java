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
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import pit12.Pit12;
import pit12.runtime.config.HudPlacement;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.hud.HudElement;
import pit12.runtime.hud.HudRegistry;
import pit12.shared.rendering.UiRenderState;
import pit12.shared.rendering.UiRenderer;

final class HudEditorController {
    private final HudRegistry registry;
    private final UiRenderer renderer;
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
        this.registry = Objects.requireNonNull(registry, "registry");
        Minecraft minecraft = Minecraft.getMinecraft();
        renderer = new UiRenderer(minecraft,
                new ResourceLocation(Pit12.MOD_ID, "fonts/montserrat-regular.ttf"),
                new ResourceLocation(Pit12.MOD_ID, "fonts/noto-sans-sc.otf"));
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
        cancelDrag();
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
            List<HudElement> elements = registry.elements();
            if (elements.isEmpty()) {
                return;
            }
            for (HudElement element : elements) {
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
        int width = scaledWidth(dragging);
        int height = scaledHeight(dragging);
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
            int width = scaledWidth(dragging);
            int height = scaledHeight(dragging);
            dragging.config().placement(HudPlacement.fromOrigin(dragX, dragY, width, height,
                    screenWidth, screenHeight));
        }
        dragging = null;
    }

    void mouseWheel(int delta) {
        // A mid-drag scale change invalidates the stored drag offsets, so the wheel only
        // applies once the element is released.
        if (selected == null || delta == 0 || dragging != null) {
            return;
        }
        IntegerSetting scale = selected.config().scale();
        int next = scale.get().intValue() + Integer.signum(delta) * 5;
        scale.set(Integer.valueOf(clamp(next, scale.minimum(), scale.maximum())));
    }

    void cancelDrag() {
        dragging = null;
    }

    void dispose() {
        close();
        selected = null;
        renderer.close();
    }

    private boolean beginDrag(HudElement element, int mouseX, int mouseY) {
        int width = scaledWidth(element);
        int height = scaledHeight(element);
        int x = originX(element, width);
        int y = originY(element, height);
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
        int width = scaledWidth(element);
        int height = scaledHeight(element);
        int x = originX(element, width);
        int y = originY(element, height);
        float scale = element.config().scaleFactor();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        try {
            element.render(partialTicks, true);
        } finally {
            GlStateManager.popMatrix();
        }
        boolean hovered = contains(mouseX, mouseY, x, y, width, height);
        int color = element == selected ? 0xFF26CEAA : hovered ? 0xFFD1D1D1 : 0x80909090;
        outline(x, y, width, height, color);
        if (element == selected || hovered) {
            String label = element.displayName() + "  " + element.config().scale().get() + "%";
            int labelX =
                    clamp(x, 1, Math.max(1, screenWidth - renderer.textWidth(label, 8.0F) - 1));
            int fontHeight = renderer.fontHeight(8.0F);
            int labelY = clamp(y >= fontHeight + 3 ? y - fontHeight - 2 : y + height + 2, 1,
                    Math.max(1, screenHeight - fontHeight - 1));
            renderer.text(label, labelX, labelY, 8.0F, 0xFFF0F0F0, true);
        }
    }

    private int scaledWidth(HudElement element) {
        prepare(element);
        return Math.max(1,
                Math.round(Math.max(1, element.width()) * element.config().scaleFactor()));
    }

    private int scaledHeight(HudElement element) {
        prepare(element);
        return Math.max(1,
                Math.round(Math.max(1, element.height()) * element.config().scaleFactor()));
    }

    private void prepare(HudElement element) {
        element.resize(pixelScale * element.config().scaleFactor());
    }

    private int originX(HudElement element, int width) {
        return element == dragging ? dragX
                : clamp(element.config().resolveX(screenWidth, width), 0,
                        Math.max(0, screenWidth - width));
    }

    private int originY(HudElement element, int height) {
        return element == dragging ? dragY
                : clamp(element.config().resolveY(screenHeight, height), 0,
                        Math.max(0, screenHeight - height));
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
