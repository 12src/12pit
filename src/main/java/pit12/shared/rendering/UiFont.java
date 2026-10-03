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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

final class UiFont {
    private static final int FIRST_CHARACTER = 32;
    private static final int LAST_CHARACTER = 255;
    private final Glyph[] glyphs = new Glyph[LAST_CHARACTER + 1];
    private final Map<String, Integer> widthCache =
            new LinkedHashMap<String, Integer>(256, 0.75F, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                    return size() > 512;
                }
            };
    private final DynamicTexture texture;
    private final int atlasSize;
    private final int height;
    private final int boldOffset;

    UiFont(Minecraft minecraft, ResourceLocation fontLocation, float logicalFontSize,
            float pixelScale) {
        float fontSize = Math.max(1.0F, logicalFontSize * pixelScale);
        Font font = loadFont(minecraft, fontLocation, fontSize);
        boldOffset = Math.max(1, Math.round(pixelScale));
        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D metricsGraphics = graphics(metricsImage, font);
        FontMetrics metrics;
        FontRenderContext context;
        try {
            metrics = metricsGraphics.getFontMetrics();
            context = metricsGraphics.getFontRenderContext();
        } finally {
            metricsGraphics.dispose();
            metricsImage.flush();
        }
        int lineHeight = metrics.getHeight();
        GlyphVector[] vectors = new GlyphVector[LAST_CHARACTER + 1];
        Rectangle[] bounds = new Rectangle[LAST_CHARACTER + 1];
        int cellHeight = 2;
        int maxCellWidth = 2;
        for (int codePoint = FIRST_CHARACTER; codePoint <= LAST_CHARACTER; codePoint++) {
            char character = (char) codePoint;
            if (!font.canDisplay(character)) {
                continue;
            }
            GlyphVector vector = font.createGlyphVector(context, Character.toString(character));
            Rectangle pixelBounds = vector.getPixelBounds(context, 0.0F, metrics.getAscent());
            vectors[codePoint] = vector;
            bounds[codePoint] = pixelBounds;
            maxCellWidth = Math.max(maxCellWidth, pixelBounds.width + 2);
            cellHeight = Math.max(cellHeight, pixelBounds.height + 2);
        }
        int size = 512;
        while ((size - 2) / maxCellWidth * ((size - 2) / cellHeight) < LAST_CHARACTER
                - FIRST_CHARACTER + 1) {
            size *= 2;
        }
        atlasSize = size;
        BufferedImage atlas = new BufferedImage(atlasSize, atlasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = graphics(atlas, font);
        int cursorX = 1;
        int cursorY = 1;
        for (int codePoint = FIRST_CHARACTER; codePoint <= LAST_CHARACTER; codePoint++) {
            GlyphVector vector = vectors[codePoint];
            if (vector == null) {
                continue;
            }
            Rectangle pixelBounds = bounds[codePoint];
            int cellWidth = pixelBounds.width + 2;
            if (cursorX + cellWidth >= atlasSize) {
                cursorX = 1;
                cursorY += cellHeight;
            }
            graphics.drawGlyphVector(vector, cursorX + 1 - pixelBounds.x,
                    cursorY + 1 + metrics.getAscent() - pixelBounds.y);
            glyphs[codePoint] =
                    new Glyph(cursorX + 1, cursorY + 1, pixelBounds.width, pixelBounds.height,
                            pixelBounds.x, pixelBounds.y, vector.getGlyphMetrics(0).getAdvanceX());
            cursorX += cellWidth;
        }
        height = lineHeight;
        graphics.dispose();
        // TextureManager keeps deleted dynamic textures in its registry, so the font owns this texture directly.
        try {
            texture = new DynamicTexture(atlas);
        } finally {
            atlas.flush();
        }
        GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    int width(String text) {
        Integer cached = widthCache.get(text);
        if (cached != null) {
            return cached;
        }
        float width = 0.0F;
        boolean formatting = false;
        boolean bold = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (formatting) {
                formatting = false;
                if (McFormatting.isBold(character)) {
                    bold = true;
                } else if (McFormatting.resetsStyle(character)) {
                    bold = false;
                }
                continue;
            }
            if (character == '\u00A7') {
                formatting = true;
                continue;
            }
            width += glyphs[character].advance + (bold ? boldOffset : 0);
        }
        int pixelWidth = Math.round(width);
        widthCache.put(text, pixelWidth);
        return pixelWidth;
    }

    boolean canRender(String text) {
        boolean formatting = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (formatting) {
                formatting = false;
                continue;
            }
            if (character == '\u00A7') {
                formatting = true;
                continue;
            }
            if (character > LAST_CHARACTER || glyphs[character] == null) {
                return false;
            }
        }
        return true;
    }

    int height() {
        return height;
    }

    void draw(String text, int x, int y, int color, boolean shadow) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.bindTexture(texture.getGlTextureId());
        int currentColor = color;
        boolean bold = false;
        setColor(currentColor);
        GL11.glBegin(GL11.GL_QUADS);
        try {
            float cursor = 0.0F;
            boolean formatting = false;
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                if (formatting) {
                    formatting = false;
                    int formattedColor = McFormatting.color(character, color, currentColor, shadow);
                    if (formattedColor != currentColor) {
                        currentColor = formattedColor;
                        setColor(currentColor);
                    }
                    if (McFormatting.isBold(character)) {
                        bold = true;
                    } else if (McFormatting.resetsStyle(character)) {
                        bold = false;
                    }
                    continue;
                }
                if (character == '\u00A7') {
                    formatting = true;
                    continue;
                }
                Glyph glyph = glyphs[character];
                // Keep fractional advances until placement so small scale errors do not add up along a line.
                int cursorX = x + Math.round(cursor) + glyph.offsetX;
                int glyphY = y + glyph.offsetY;
                // Quad edges map to texel edges; pixel centers then sample texel centers without stretching.
                float left = (float) glyph.x / atlasSize;
                float top = (float) glyph.y / atlasSize;
                float right = (float) (glyph.x + glyph.width) / atlasSize;
                float bottom = (float) (glyph.y + glyph.height) / atlasSize;
                GL11.glTexCoord2f(left, top);
                GL11.glVertex2i(cursorX, glyphY);
                GL11.glTexCoord2f(left, bottom);
                GL11.glVertex2i(cursorX, glyphY + glyph.height);
                GL11.glTexCoord2f(right, bottom);
                GL11.glVertex2i(cursorX + glyph.width, glyphY + glyph.height);
                GL11.glTexCoord2f(right, top);
                GL11.glVertex2i(cursorX + glyph.width, glyphY);
                if (bold) {
                    GL11.glTexCoord2f(left, top);
                    GL11.glVertex2i(cursorX + boldOffset, glyphY);
                    GL11.glTexCoord2f(left, bottom);
                    GL11.glVertex2i(cursorX + boldOffset, glyphY + glyph.height);
                    GL11.glTexCoord2f(right, bottom);
                    GL11.glVertex2i(cursorX + glyph.width + boldOffset, glyphY + glyph.height);
                    GL11.glTexCoord2f(right, top);
                    GL11.glVertex2i(cursorX + glyph.width + boldOffset, glyphY);
                }
                cursor += glyph.advance + (bold ? boldOffset : 0);
            }
        } finally {
            try {
                GL11.glEnd();
            } finally {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    private static void setColor(int color) {
        GlStateManager.color((color >>> 16 & 0xFF) / 255.0F, (color >>> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, (color >>> 24 & 0xFF) / 255.0F);
    }

    void close() {
        texture.deleteGlTexture();
    }

    private static Graphics2D graphics(BufferedImage image, Font font) {
        Graphics2D graphics = image.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return graphics;
    }

    private static Font loadFont(Minecraft minecraft, ResourceLocation fontLocation,
            float fontSize) {
        try (InputStream input =
                minecraft.getResourceManager().getResource(fontLocation).getInputStream()) {
            return Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(Font.PLAIN, fontSize);
        } catch (FontFormatException | IOException failure) {
            throw new IllegalStateException("Unable to load UI font", failure);
        }
    }

    private static final class Glyph {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int offsetX;
        private final int offsetY;
        private final float advance;

        private Glyph(int x, int y, int width, int height, int offsetX, int offsetY,
                float advance) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.advance = advance;
        }
    }
}
