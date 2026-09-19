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
package pit12.shared.rendering;

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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

final class UiTextCache {
    private static final int MAX_ENTRIES = 128;
    private final Minecraft minecraft;
    private final float pixelScale;
    private final Font font;
    private final FontMetrics metrics;
    private final Map<String, TextureEntry> entries =
            new LinkedHashMap<String, TextureEntry>(MAX_ENTRIES, 0.75F, true);

    UiTextCache(Minecraft minecraft, ResourceLocation fontLocation, float logicalFontSize,
            float pixelScale) {
        this(minecraft, loadFont(minecraft, fontLocation,
                Math.max(1.0F, Math.round(logicalFontSize * pixelScale))), pixelScale);
    }

    UiTextCache(Minecraft minecraft, float logicalFontSize, float pixelScale) {
        this(minecraft, new Font(Font.SANS_SERIF, Font.PLAIN,
                Math.max(1, Math.round(logicalFontSize * pixelScale))), pixelScale);
    }

    private UiTextCache(Minecraft minecraft, Font font, float pixelScale) {
        this.minecraft = minecraft;
        this.pixelScale = pixelScale;
        this.font = font;
        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = metricsImage.createGraphics();
        try {
            graphics.setFont(font);
            metrics = graphics.getFontMetrics();
        } finally {
            graphics.dispose();
            metricsImage.flush();
        }
    }

    boolean draw(String text, float x, float y, int color) {
        TextureEntry entry = entry(text);
        if (entry == null) {
            return false;
        }
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
            minecraft.getTextureManager().bindTexture(entry.location);
            GlStateManager.color(red, green, blue, alpha);
            Gui.drawModalRectWithCustomSizedTexture(pixelX, pixelY, 0.0F, 0.0F, entry.width,
                    entry.height, entry.width, entry.height);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
        return true;
    }

    int width(String text) {
        TextureEntry entry = entry(text);
        return entry == null ? -1 : (int) Math.ceil(entry.width / pixelScale);
    }

    void close() {
        for (TextureEntry entry : entries.values()) {
            minecraft.getTextureManager().deleteTexture(entry.location);
        }
        entries.clear();
    }

    private TextureEntry entry(String text) {
        String plainText = plainText(text);
        TextureEntry cached = entries.get(plainText);
        if (cached != null) {
            return cached;
        }
        if (font.canDisplayUpTo(plainText) >= 0) {
            return null;
        }
        int textWidth = Math.max(1, metrics.stringWidth(plainText));
        int textHeight = Math.max(1, metrics.getHeight());
        BufferedImage image =
                new BufferedImage(textWidth + 2, textHeight + 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setComposite(AlphaComposite.Src);
            graphics.setFont(font);
            graphics.setColor(Color.WHITE);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.drawString(plainText, 1, metrics.getAscent() + 1);
        } finally {
            graphics.dispose();
        }
        DynamicTexture dynamicTexture = new DynamicTexture(image);
        image.flush();
        ResourceLocation location = minecraft.getTextureManager()
                .getDynamicTextureLocation("pit12-noto-text", dynamicTexture);
        TextureEntry created = new TextureEntry(location, textWidth + 2, textHeight + 2);
        entries.put(plainText, created);
        trimOldest();
        return created;
    }

    private void trimOldest() {
        while (entries.size() > MAX_ENTRIES) {
            Iterator<Map.Entry<String, TextureEntry>> iterator = entries.entrySet().iterator();
            TextureEntry oldest = iterator.next().getValue();
            iterator.remove();
            minecraft.getTextureManager().deleteTexture(oldest.location);
        }
    }

    private static String plainText(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean formatting = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (formatting) {
                formatting = false;
            } else if (character == '\u00A7') {
                formatting = true;
            } else {
                result.append(character == '\n' ? ' ' : character);
            }
        }
        return result.toString();
    }

    private static Font loadFont(Minecraft minecraft, ResourceLocation fontLocation,
            float fontSize) {
        try (InputStream input =
                minecraft.getResourceManager().getResource(fontLocation).getInputStream()) {
            return Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(Font.PLAIN, fontSize);
        } catch (FontFormatException | IOException failure) {
            throw new IllegalStateException("Unable to load fallback UI font", failure);
        }
    }

    private static final class TextureEntry {
        private final ResourceLocation location;
        private final int width;
        private final int height;

        private TextureEntry(ResourceLocation location, int width, int height) {
            this.location = location;
            this.width = width;
            this.height = height;
        }
    }
}
