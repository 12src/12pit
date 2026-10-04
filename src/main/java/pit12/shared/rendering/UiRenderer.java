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

import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class UiRenderer {
    private static final Logger LOGGER = Logger.getLogger(UiRenderer.class.getName());
    private final Minecraft minecraft;
    private final FontRenderer fallbackFont;
    private final ResourceLocation fontLocation;
    private UiFont font;
    private UiTextCache systemText;
    private boolean fontAttempted;
    private boolean systemAttempted;
    private float pixelScale = Float.NaN;

    public UiRenderer(Minecraft minecraft, ResourceLocation fontLocation) {
        this.minecraft = minecraft;
        fallbackFont = minecraft.fontRendererObj;
        this.fontLocation = fontLocation;
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

    public void text(String text, int x, int y, int color, boolean shadow, boolean useCustomFont) {
        FontBackend backend = fontBackend(text, useCustomFont);
        if (backend == FontBackend.VANILLA) {
            fallbackFont.drawString(text, x, y, color, shadow);
            return;
        }
        if (backend == FontBackend.CUSTOM) {
            drawCustomText(text, x, y, color, shadow);
            return;
        }
        if (shadow) {
            systemText.draw(text, x + 1, y + 1, McFormatting.shadowColor(color), true);
        }
        systemText.draw(text, x, y, color, false);
    }

    public void texture(ResourceLocation texture, int x, int y, int width, int height, int color,
            float rotationDegrees) {
        if (width <= 0 || height <= 0 || Float.isNaN(pixelScale)) {
            return;
        }
        int pixelX = Math.round(x * pixelScale);
        int pixelY = Math.round(y * pixelScale);
        int pixelWidth = Math.max(1, Math.round(width * pixelScale));
        int pixelHeight = Math.max(1, Math.round(height * pixelScale));
        float inverseScale = 1.0F / pixelScale;
        GlStateManager.pushMatrix();
        GlStateManager.scale(inverseScale, inverseScale, 1.0F);
        GlStateManager.translate(pixelX + pixelWidth / 2.0F, pixelY + pixelHeight / 2.0F, 0.0F);
        GlStateManager.rotate(rotationDegrees, 0.0F, 0.0F, 1.0F);
        try {
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1,
                    0);
            minecraft.getTextureManager().bindTexture(texture);
            color(color);
            Gui.drawModalRectWithCustomSizedTexture(-pixelWidth / 2, -pixelHeight / 2, 0.0F, 0.0F,
                    pixelWidth, pixelHeight, pixelWidth, pixelHeight);
        } finally {
            color(0xFFFFFFFF);
            GlStateManager.popMatrix();
        }
    }

    public int textWidth(String text, boolean useCustomFont) {
        switch (fontBackend(text, useCustomFont)) {
            case CUSTOM:
                return (int) Math.ceil(font.width(text) / pixelScale);
            case SYSTEM:
                return systemText.width(text);
            default:
                return fallbackFont.getStringWidth(text);
        }
    }

    public int fontHeight(String text, boolean useCustomFont) {
        switch (fontBackend(text, useCustomFont)) {
            case CUSTOM:
                return (int) Math.ceil(font.height() / pixelScale);
            case SYSTEM:
                return systemText.height();
            default:
                return fallbackFont.FONT_HEIGHT;
        }
    }

    public void close() {
        releaseFonts();
        pixelScale = Float.NaN;
    }

    private FontBackend fontBackend(String text, boolean useCustomFont) {
        if (Float.isNaN(pixelScale)) {
            return FontBackend.VANILLA;
        }
        if (useCustomFont) {
            UiFont font = font();
            if (font != null && font.canRender(text)) {
                return FontBackend.CUSTOM;
            }
        }
        boolean formatting = false;
        boolean vanilla = true;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (formatting) {
                formatting = false;
            } else if (character == '\u00A7') {
                formatting = true;
            } else if (fallbackFont.getCharWidth(character) <= 0) {
                vanilla = false;
                break;
            }
        }
        // Minecraft reports zero width for missing glyphs; drawString does not report them.
        if (vanilla || systemText() == null) {
            return FontBackend.VANILLA;
        }
        return FontBackend.SYSTEM;
    }

    private void drawCustomText(String text, int x, int y, int color, boolean shadow) {
        float inverseScale = 1.0F / pixelScale;
        int pixelX = Math.round(x * pixelScale);
        int pixelY = Math.round(y * pixelScale);
        // Fractional logical scales return to framebuffer pixels before glyph texture sampling.
        GlStateManager.pushMatrix();
        GlStateManager.scale(inverseScale, inverseScale, 1.0F);
        try {
            if (shadow) {
                int offset = Math.max(1, Math.round(pixelScale));
                font.draw(text, pixelX + offset, pixelY + offset, McFormatting.shadowColor(color),
                        true);
            }
            font.draw(text, pixelX, pixelY, color, false);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void releaseFonts() {
        fontAttempted = false;
        systemAttempted = false;
        if (font == null && systemText == null) {
            return;
        }
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            if (font != null) {
                font.close();
                font = null;
            }
            if (systemText != null) {
                systemText.close();
                systemText = null;
            }
        } finally {
            GlStateManager.bindTexture(boundTexture);
        }
    }

    private float snap(float coordinate) {
        return Float.isNaN(pixelScale) ? coordinate
                : Math.round(coordinate * pixelScale) / pixelScale;
    }

    private static void color(int color) {
        GlStateManager.color((color >>> 16 & 0xFF) / 255.0F, (color >>> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, (color >>> 24 & 0xFF) / 255.0F);
    }

    private UiFont font() {
        if (fontAttempted) {
            return font;
        }
        fontAttempted = true;
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            // Monocraft has nine design cells per em, so size nine keeps its grid aligned at integer scales.
            font = new UiFont(minecraft, fontLocation, 9.0F, pixelScale);
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Unable to prepare UI font", failure);
        } finally {
            GlStateManager.bindTexture(boundTexture);
        }
        return font;
    }

    private UiTextCache systemText() {
        if (systemAttempted) {
            return systemText;
        }
        systemAttempted = true;
        try {
            systemText = new UiTextCache(8.0F, pixelScale);
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Unable to prepare system UI font", failure);
        }
        return systemText;
    }

    private enum FontBackend {
        CUSTOM, VANILLA, SYSTEM
    }
}
