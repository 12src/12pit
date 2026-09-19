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
package pit12.feature.clickgui.render;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import pit12.Pit12;

final class HudRenderer {
    private static final Logger LOGGER = Logger.getLogger(HudRenderer.class.getName());
    private static final ResourceLocation NOTO_SANS_SC_LOCATION =
            new ResourceLocation(Pit12.MOD_ID, "fonts/noto-sans-sc.otf");
    private final Minecraft minecraft;
    private final FontRenderer fallbackFont;
    private final ResourceLocation fontLocation;
    private final float logicalFontSize;
    private HudFont font;
    private HudFont smallFont;
    private HudTextCache systemText;
    private HudTextCache smallSystemText;
    private HudTextCache cjkText;
    private HudTextCache smallCjkText;
    private HudTextureCache textures;
    private float pixelScale = Float.NaN;

    HudRenderer(Minecraft minecraft, ResourceLocation fontLocation, float logicalFontSize) {
        this.minecraft = minecraft;
        fallbackFont = minecraft.fontRendererObj;
        this.fontLocation = fontLocation;
        this.logicalFontSize = logicalFontSize;
    }

    void resize(float pixelScale) {
        float normalizedScale = Math.max(0.01F, pixelScale);
        if (Float.compare(this.pixelScale, normalizedScale) == 0) {
            return;
        }
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            releaseResources();
            this.pixelScale = normalizedScale;
            textures = new HudTextureCache(minecraft);
            try {
                font = new HudFont(minecraft, fontLocation, logicalFontSize, normalizedScale);
                smallFont = new HudFont(minecraft, fontLocation,
                        Math.max(1.0F, logicalFontSize - 2.0F), normalizedScale);
            } catch (RuntimeException failure) {
                LOGGER.log(Level.WARNING, "Unable to prepare HUD font", failure);
                releaseFonts();
            }
            systemText = prepareSystemFont(logicalFontSize, normalizedScale);
            smallSystemText =
                    prepareSystemFont(Math.max(1.0F, logicalFontSize - 2.0F), normalizedScale);
            cjkText = prepareFallbackFont(NOTO_SANS_SC_LOCATION, logicalFontSize, normalizedScale);
            smallCjkText = prepareFallbackFont(NOTO_SANS_SC_LOCATION,
                    Math.max(1.0F, logicalFontSize - 2.0F), normalizedScale);
        } finally {
            GlStateManager.bindTexture(boundTexture);
        }
    }

    void rect(int x, int y, int width, int height, int color) {
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

    void text(String text, int x, int y, int color) {
        if (font != null && !Float.isNaN(pixelScale) && font.canRender(text)) {
            draw(font, text, x, y, color);
        } else if (!drawFallback(cjkText, systemText, text, x, y, color)) {
            fallbackFont.drawString(text, x, y, color, false);
        }
    }

    void smallText(String text, float x, float y, int color) {
        if (smallFont != null && !Float.isNaN(pixelScale) && smallFont.canRender(text)) {
            draw(smallFont, text, x, y, color);
        } else if (!drawFallback(smallCjkText, smallSystemText, text, x, y, color)) {
            text(text, Math.round(x), Math.round(y), color);
        }
    }

    private void draw(HudFont selectedFont, String text, float x, float y, int color) {
        float inverseScale = 1.0F / pixelScale;
        int pixelX = Math.round(x * pixelScale);
        int pixelY = Math.round(y * pixelScale);
        // The caller may use a fractional logical scale, so glyphs return to framebuffer pixels before sampling.
        GlStateManager.pushMatrix();
        GlStateManager.scale(inverseScale, inverseScale, 1.0F);
        try {
            selectedFont.draw(text, pixelX, pixelY, color);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    int textWidth(String text) {
        if (font != null && !Float.isNaN(pixelScale) && font.canRender(text)) {
            return (int) Math.ceil(font.width(text) / pixelScale);
        }
        int width = fallbackWidth(cjkText, systemText, text);
        if (width >= 0) {
            return width;
        }
        return fallbackFont.getStringWidth(text);
    }

    int fontHeight() {
        if (font == null || Float.isNaN(pixelScale)) {
            return fallbackFont.FONT_HEIGHT;
        }
        return (int) Math.ceil(font.height() / pixelScale);
    }

    int smallTextWidth(String text) {
        if (smallFont != null && !Float.isNaN(pixelScale) && smallFont.canRender(text)) {
            return (int) Math.ceil(smallFont.width(text) / pixelScale);
        }
        int width = fallbackWidth(smallCjkText, smallSystemText, text);
        if (width >= 0) {
            return width;
        }
        return textWidth(text);
    }

    int smallFontHeight() {
        if (smallFont == null || Float.isNaN(pixelScale)) {
            return fontHeight();
        }
        return (int) Math.ceil(smallFont.height() / pixelScale);
    }

    ResourceLocation prepareTexture(ResourceLocation texture, int width, int height) {
        if (textures == null || Float.isNaN(pixelScale)) {
            throw new IllegalStateException(
                    "HUD renderer must be resized before preparing textures");
        }
        int pixelWidth = Math.max(1, Math.round(width * pixelScale));
        int pixelHeight = Math.max(1, Math.round(height * pixelScale));
        return textures.prepare(texture, pixelWidth, pixelHeight);
    }

    void texture(ResourceLocation texture, float x, float y, int width, int height, int color) {
        if (width <= 0 || height <= 0 || Float.isNaN(pixelScale)) {
            return;
        }
        int pixelX = Math.round(x * pixelScale);
        int pixelY = Math.round(y * pixelScale);
        int pixelWidth = Math.max(1, Math.round(width * pixelScale));
        int pixelHeight = Math.max(1, Math.round(height * pixelScale));
        float inverseScale = 1.0F / pixelScale;
        float alpha = (color >>> 24 & 0xFF) / 255.0F;
        float red = (color >>> 16 & 0xFF) / 255.0F;
        float green = (color >>> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        GlStateManager.pushMatrix();
        GlStateManager.scale(inverseScale, inverseScale, 1.0F);
        try {
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1,
                    0);
            minecraft.getTextureManager().bindTexture(texture);
            GlStateManager.color(red, green, blue, alpha);
            Gui.drawModalRectWithCustomSizedTexture(pixelX, pixelY, 0.0F, 0.0F, pixelWidth,
                    pixelHeight, pixelWidth, pixelHeight);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    void close() {
        releaseResources();
        pixelScale = Float.NaN;
    }

    private float snap(float coordinate) {
        return Float.isNaN(pixelScale) ? coordinate
                : Math.round(coordinate * pixelScale) / pixelScale;
    }

    private void releaseResources() {
        releaseFonts();
        releaseUnicodeText();
        if (textures != null) {
            textures.close();
            textures = null;
        }
    }

    private void releaseFonts() {
        if (font != null) {
            font.close();
            font = null;
        }
        if (smallFont != null) {
            smallFont.close();
            smallFont = null;
        }
    }

    private void releaseUnicodeText() {
        if (systemText != null) {
            systemText.close();
            systemText = null;
        }
        if (smallSystemText != null) {
            smallSystemText.close();
            smallSystemText = null;
        }
        if (cjkText != null) {
            cjkText.close();
            cjkText = null;
        }
        if (smallCjkText != null) {
            smallCjkText.close();
            smallCjkText = null;
        }
    }

    private HudTextCache prepareFallbackFont(ResourceLocation location, float fontSize,
            float normalizedScale) {
        try {
            return new HudTextCache(minecraft, location, fontSize, normalizedScale);
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Unable to prepare fallback HUD font " + location, failure);
            return null;
        }
    }

    private HudTextCache prepareSystemFont(float fontSize, float normalizedScale) {
        try {
            return new HudTextCache(minecraft, fontSize, normalizedScale);
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Unable to prepare system fallback HUD font", failure);
            return null;
        }
    }

    private static boolean drawFallback(HudTextCache primary, HudTextCache cjk, String text,
            float x, float y, int color) {
        return primary != null && primary.draw(text, x, y, color)
                || cjk != null && cjk.draw(text, x, y, color);
    }

    private static int fallbackWidth(HudTextCache primary, HudTextCache cjk, String text) {
        if (primary != null) {
            int width = primary.width(text);
            if (width >= 0) {
                return width;
            }
        }
        return cjk == null ? -1 : cjk.width(text);
    }

    private static void color(int color) {
        GlStateManager.color((color >>> 16 & 0xFF) / 255.0F, (color >>> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, (color >>> 24 & 0xFF) / 255.0F);
    }
}
