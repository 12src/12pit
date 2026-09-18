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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

final class HudFont {
    private static final int ATLAS_SIZE = 512;
    private static final int FIRST_CHARACTER = 32;
    private static final int LAST_CHARACTER = 255;
    private final Minecraft minecraft;
    private final Glyph[] glyphs = new Glyph[LAST_CHARACTER + 1];
    private final Map<String, Integer> widthCache =
            new LinkedHashMap<String, Integer>(256, 0.75F, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                    return size() > 512;
                }
            };
    private final ResourceLocation texture;
    private final int height;

    HudFont(Minecraft minecraft, ResourceLocation fontLocation, float logicalFontSize,
            float pixelScale) {
        this.minecraft = minecraft;
        float fontSize = Math.max(1.0F, Math.round(logicalFontSize * pixelScale));
        Font font = loadFont(minecraft, fontLocation, fontSize);
        BufferedImage atlas =
                new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontMetrics metrics = graphics.getFontMetrics();
        int lineHeight = metrics.getHeight();
        int cellHeight = lineHeight + 2;
        int cursorX = 1;
        int cursorY = 1;
        for (int codePoint = FIRST_CHARACTER; codePoint <= LAST_CHARACTER; codePoint++) {
            char character = (char) codePoint;
            int advance = Math.max(1, metrics.charWidth(character));
            int cellWidth = advance + 3;
            if (cursorX + cellWidth >= ATLAS_SIZE) {
                cursorX = 1;
                cursorY += cellHeight;
            }
            if (cursorY + cellHeight >= ATLAS_SIZE) {
                break;
            }
            graphics.drawString(Character.toString(character), cursorX + 1,
                    cursorY + metrics.getAscent() + 1);
            glyphs[codePoint] = new Glyph(cursorX, cursorY + 1, cellWidth, lineHeight, advance);
            cursorX += cellWidth;
        }
        height = lineHeight;
        graphics.dispose();
        DynamicTexture dynamicTexture = new DynamicTexture(atlas);
        texture = minecraft.getTextureManager().getDynamicTextureLocation("pit12-hud-font",
                dynamicTexture);
        minecraft.getTextureManager().bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    int width(String text) {
        Integer cached = widthCache.get(text);
        if (cached != null) {
            return cached.intValue();
        }
        int width = 0;
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
            width += glyph(character).advance;
        }
        widthCache.put(text, Integer.valueOf(width));
        return width;
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

    void draw(String text, int x, int y, int color) {
        float alpha = (color >>> 24 & 0xFF) / 255.0F;
        float red = (color >>> 16 & 0xFF) / 255.0F;
        float green = (color >>> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        minecraft.getTextureManager().bindTexture(texture);
        GlStateManager.color(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        try {
            int cursorX = x;
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
                Glyph glyph = glyph(character);
                float left = (glyph.x + 0.5F) / (float) ATLAS_SIZE;
                float top = (glyph.y + 0.5F) / (float) ATLAS_SIZE;
                float right = (glyph.x + glyph.width - 0.5F) / (float) ATLAS_SIZE;
                float bottom = (glyph.y + glyph.height - 0.5F) / (float) ATLAS_SIZE;
                GL11.glTexCoord2f(left, top);
                GL11.glVertex2i(cursorX, y);
                GL11.glTexCoord2f(left, bottom);
                GL11.glVertex2i(cursorX, y + glyph.height);
                GL11.glTexCoord2f(right, bottom);
                GL11.glVertex2i(cursorX + glyph.width, y + glyph.height);
                GL11.glTexCoord2f(right, top);
                GL11.glVertex2i(cursorX + glyph.width, y);
                cursorX += glyph.advance;
            }
        } finally {
            try {
                GL11.glEnd();
            } finally {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    void close() {
        minecraft.getTextureManager().deleteTexture(texture);
    }

    private Glyph glyph(char character) {
        Glyph glyph = character <= LAST_CHARACTER ? glyphs[character] : null;
        if (glyph == null) {
            glyph = glyphs['?'];
        }
        return glyph;
    }

    private static Font loadFont(Minecraft minecraft, ResourceLocation fontLocation,
            float fontSize) {
        try (InputStream input =
                minecraft.getResourceManager().getResource(fontLocation).getInputStream()) {
            return Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(Font.PLAIN, fontSize);
        } catch (FontFormatException | IOException failure) {
            throw new IllegalStateException("Unable to load HUD font", failure);
        }
    }

    private static final class Glyph {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int advance;

        private Glyph(int x, int y, int width, int height, int advance) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.advance = advance;
        }
    }
}
