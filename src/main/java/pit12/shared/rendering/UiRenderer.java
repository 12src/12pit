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
package pit12.shared.rendering;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 2D UI renderer with persistent per-size font resources. Each distinct font size allocates its own glyph atlas and
 * text caches; callers must use a small, fixed set of sizes.
 */
public final class UiRenderer {
    private static final Logger LOGGER = Logger.getLogger(UiRenderer.class.getName());
    private final Minecraft minecraft;
    private final FontRenderer fallbackFont;
    private final ResourceLocation fontLocation;
    private final ResourceLocation fallbackFontLocation;
    private final Map<Float, FontResources> fonts = new HashMap<Float, FontResources>();
    private float pixelScale = Float.NaN;

    public UiRenderer(Minecraft minecraft, ResourceLocation fontLocation,
            ResourceLocation fallbackFontLocation) {
        this.minecraft = minecraft;
        fallbackFont = minecraft.fontRendererObj;
        this.fontLocation = fontLocation;
        this.fallbackFontLocation = fallbackFontLocation;
    }

    public void resize(float pixelScale) {
        float normalizedScale = Math.max(0.01F, pixelScale);
        if (Float.compare(this.pixelScale, normalizedScale) == 0) {
            return;
        }
        releaseFonts();
        this.pixelScale = normalizedScale;
    }

    public void rect(int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        float left = snap(x);
        float top = snap(y);
        float right = snap(x + width);
        float bottom = snap(y + height);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        color(color);
        GL11.glBegin(GL11.GL_QUADS);
        try {
            GL11.glVertex2f(left, bottom);
            GL11.glVertex2f(right, bottom);
            GL11.glVertex2f(right, top);
            GL11.glVertex2f(left, top);
        } finally {
            try {
                GL11.glEnd();
            } finally {
                GlStateManager.enableTexture2D();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    public void text(String text, int x, int y, float fontSize, int color) {
        text(text, x, y, fontSize, color, false);
    }

    public void text(String text, int x, int y, float fontSize, int color, boolean shadow) {
        if (shadow) {
            drawText(text, x + 1, y + 1, fontSize, shadowColor(color));
        }
        drawText(text, x, y, fontSize, color);
    }

    public int textWidth(String text, float fontSize) {
        FontResources resources = fontResources(fontSize);
        if (resources != null && resources.font != null && resources.font.canRender(text)) {
            return (int) Math.ceil(resources.font.width(text) / pixelScale);
        }
        int width = resources == null ? -1
                : fallbackWidth(resources.cjkText, resources.systemText, text);
        return width >= 0 ? width : fallbackFont.getStringWidth(text);
    }

    public int fontHeight(float fontSize) {
        FontResources resources = fontResources(fontSize);
        if (resources == null || resources.font == null) {
            return fallbackFont.FONT_HEIGHT;
        }
        return (int) Math.ceil(resources.font.height() / pixelScale);
    }

    public void close() {
        releaseFonts();
        pixelScale = Float.NaN;
    }

    private void drawText(String text, int x, int y, float fontSize, int color) {
        FontResources resources = fontResources(fontSize);
        if (resources != null && resources.font != null && resources.font.canRender(text)) {
            draw(resources.font, text, x, y, color);
        } else if (resources == null
                || !drawFallback(resources.cjkText, resources.systemText, text, x, y, color)) {
            fallbackFont.drawString(text, x, y, color, false);
        }
    }

    private void draw(UiFont font, String text, float x, float y, int color) {
        float inverseScale = 1.0F / pixelScale;
        int pixelX = Math.round(x * pixelScale);
        int pixelY = Math.round(y * pixelScale);
        // Fractional logical scales return to framebuffer pixels before glyph texture sampling.
        GlStateManager.pushMatrix();
        GlStateManager.scale(inverseScale, inverseScale, 1.0F);
        try {
            font.draw(text, pixelX, pixelY, color);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private FontResources fontResources(float fontSize) {
        if (!(fontSize > 0.0F) || Float.isInfinite(fontSize)) {
            throw new IllegalArgumentException("fontSize must be finite and greater than zero");
        }
        if (Float.isNaN(pixelScale)) {
            return null;
        }
        Float key = Float.valueOf(fontSize);
        FontResources cached = fonts.get(key);
        if (cached != null) {
            return cached;
        }
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            FontResources created = new FontResources(fontSize);
            fonts.put(key, created);
            return created;
        } finally {
            GlStateManager.bindTexture(boundTexture);
        }
    }

    private void releaseFonts() {
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            for (FontResources resources : fonts.values()) {
                resources.close();
            }
            fonts.clear();
        } finally {
            GlStateManager.bindTexture(boundTexture);
        }
    }

    private float snap(float coordinate) {
        return Float.isNaN(pixelScale) ? coordinate
                : Math.round(coordinate * pixelScale) / pixelScale;
    }

    private UiTextCache prepareFallbackFont(ResourceLocation location, float fontSize) {
        try {
            return new UiTextCache(minecraft, location, fontSize, pixelScale);
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Unable to prepare fallback UI font " + location, failure);
            return null;
        }
    }

    private UiTextCache prepareSystemFont(float fontSize) {
        try {
            return new UiTextCache(minecraft, fontSize, pixelScale);
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Unable to prepare system fallback UI font", failure);
            return null;
        }
    }

    private static boolean drawFallback(UiTextCache primary, UiTextCache secondary, String text,
            float x, float y, int color) {
        return primary != null && primary.draw(text, x, y, color)
                || secondary != null && secondary.draw(text, x, y, color);
    }

    private static int fallbackWidth(UiTextCache primary, UiTextCache secondary, String text) {
        if (primary != null) {
            int width = primary.width(text);
            if (width >= 0) {
                return width;
            }
        }
        return secondary == null ? -1 : secondary.width(text);
    }

    private static void color(int color) {
        GlStateManager.color((color >>> 16 & 0xFF) / 255.0F, (color >>> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, (color >>> 24 & 0xFF) / 255.0F);
    }

    private static int shadowColor(int color) {
        return color & 0xFF000000 | (color & 0x00FCFCFC) >> 2;
    }

    private final class FontResources {
        private UiFont font;
        private final UiTextCache systemText;
        private final UiTextCache cjkText;

        private FontResources(float fontSize) {
            try {
                font = new UiFont(minecraft, fontLocation, fontSize, pixelScale);
            } catch (RuntimeException failure) {
                LOGGER.log(Level.WARNING, "Unable to prepare UI font", failure);
            }
            systemText = prepareSystemFont(fontSize);
            cjkText = prepareFallbackFont(fallbackFontLocation, fontSize);
        }

        private void close() {
            if (font != null) {
                font.close();
                font = null;
            }
            if (systemText != null) {
                systemText.close();
            }
            if (cjkText != null) {
                cjkText.close();
            }
        }
    }
}
