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
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;

final class UiTextCache {
    private static final int MAX_ENTRIES = 128;
    private final float pixelScale;
    private final Font[] fonts = new Font[2];
    private final FontMetrics[] metrics = new FontMetrics[2];
    private final Map<String, TextureEntry> entries =
            new LinkedHashMap<String, TextureEntry>(MAX_ENTRIES, 0.75F, true);

    UiTextCache(float logicalFontSize, float pixelScale) {
        this.pixelScale = pixelScale;
        fonts[0] = new Font(Font.SANS_SERIF, Font.PLAIN,
                Math.max(1, Math.round(logicalFontSize * pixelScale)));
        fonts[1] = fonts[0].deriveFont(Font.BOLD);
        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = metricsImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            metrics[0] = graphics.getFontMetrics(fonts[0]);
            metrics[1] = graphics.getFontMetrics(fonts[1]);
        } finally {
            graphics.dispose();
            metricsImage.flush();
        }
    }

    void draw(String text, float x, float y, int color, boolean shadow) {
        int runStart = 0;
        int cursor = 0;
        int currentColor = color;
        boolean bold = false;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) != '\u00A7') {
                continue;
            }
            String run = text.substring(runStart, index);
            if (!run.isEmpty()) {
                cursor += drawPlain(run, x + cursor / pixelScale, y, currentColor, bold);
            }
            if (index + 1 == text.length()) {
                return;
            }
            char code = text.charAt(index + 1);
            currentColor = McFormatting.color(code, color, currentColor, shadow);
            if (McFormatting.isBold(code)) {
                bold = true;
            } else if (McFormatting.resetsStyle(code)) {
                bold = false;
            }
            index++;
            runStart = index + 1;
        }
        String run = text.substring(runStart);
        if (!run.isEmpty()) {
            drawPlain(run, x + cursor / pixelScale, y, currentColor, bold);
        }
    }

    private int drawPlain(String text, float x, float y, int color, boolean bold) {
        TextureEntry entry = entry(text, bold);
        int pixelX = Math.round(x * pixelScale);
        int pixelY = Math.round(y * pixelScale);
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0F / pixelScale, 1.0F / pixelScale, 1.0F);
        try {
            float alpha = (color >>> 24 & 0xFF) / 255.0F;
            float red = (color >>> 16 & 0xFF) / 255.0F;
            float green = (color >>> 8 & 0xFF) / 255.0F;
            float blue = (color & 0xFF) / 255.0F;
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1,
                    0);
            GlStateManager.bindTexture(entry.texture.getGlTextureId());
            GlStateManager.color(red, green, blue, alpha);
            Gui.drawModalRectWithCustomSizedTexture(pixelX - 1, pixelY - 1, 0.0F, 0.0F, entry.width,
                    entry.height, entry.width, entry.height);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
        return entry.advance;
    }

    int width(String text) {
        int width = 0;
        int runStart = 0;
        boolean bold = false;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) != '\u00A7') {
                continue;
            }
            width += runWidth(text.substring(runStart, index), bold);
            if (index + 1 == text.length()) {
                return (int) Math.ceil(width / pixelScale);
            }
            char code = text.charAt(index + 1);
            if (McFormatting.isBold(code)) {
                bold = true;
            } else if (McFormatting.resetsStyle(code)) {
                bold = false;
            }
            index++;
            runStart = index + 1;
        }
        return (int) Math.ceil((width + runWidth(text.substring(runStart), bold)) / pixelScale);
    }

    int height() {
        return (int) Math
                .ceil(Math.max(metrics[0].getHeight(), metrics[1].getHeight()) / pixelScale);
    }

    private int runWidth(String text, boolean bold) {
        String plainText = text.replace('\n', ' ');
        TextureEntry cached = entries.get(bold ? "\u00A7l" + plainText : plainText);
        return cached == null ? metrics[bold ? 1 : 0].stringWidth(plainText) : cached.advance;
    }

    void close() {
        for (TextureEntry entry : entries.values()) {
            entry.texture.deleteGlTexture();
        }
        entries.clear();
    }

    private TextureEntry entry(String text, boolean bold) {
        String plainText = text.replace('\n', ' ');
        String key = bold ? "\u00A7l" + plainText : plainText;
        TextureEntry cached = entries.get(key);
        if (cached != null) {
            return cached;
        }
        FontMetrics runMetrics = metrics[bold ? 1 : 0];
        int advance = runMetrics.stringWidth(plainText);
        int textWidth = Math.max(1, advance);
        int textHeight = Math.max(1, runMetrics.getHeight());
        BufferedImage image =
                new BufferedImage(textWidth + 2, textHeight + 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setComposite(AlphaComposite.Src);
            graphics.setFont(fonts[bold ? 1 : 0]);
            graphics.setColor(Color.WHITE);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.drawString(plainText, 1, runMetrics.getAscent() + 1);
        } finally {
            graphics.dispose();
        }
        DynamicTexture texture;
        try {
            texture = new DynamicTexture(image);
        } finally {
            image.flush();
        }
        TextureEntry created = new TextureEntry(texture, textWidth + 2, textHeight + 2, advance);
        entries.put(key, created);
        trimOldest();
        return created;
    }

    private void trimOldest() {
        while (entries.size() > MAX_ENTRIES) {
            Iterator<Map.Entry<String, TextureEntry>> iterator = entries.entrySet().iterator();
            TextureEntry oldest = iterator.next().getValue();
            iterator.remove();
            oldest.texture.deleteGlTexture();
        }
    }

    private static final class TextureEntry {
        private final DynamicTexture texture;
        private final int width;
        private final int height;
        private final int advance;

        private TextureEntry(DynamicTexture texture, int width, int height, int advance) {
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.advance = advance;
        }
    }
}
