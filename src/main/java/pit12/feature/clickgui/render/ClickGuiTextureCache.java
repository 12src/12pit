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
package pit12.feature.clickgui.render;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

final class ClickGuiTextureCache {
    private static final Logger LOGGER = Logger.getLogger(ClickGuiTextureCache.class.getName());
    private final Minecraft minecraft;
    private final Map<TextureKey, TextureEntry> textures = new HashMap<TextureKey, TextureEntry>();

    ClickGuiTextureCache(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    ResourceLocation prepare(ResourceLocation source, int width, int height) {
        TextureKey key = new TextureKey(source, width, height);
        TextureEntry cached = textures.get(key);
        if (cached != null) {
            return cached.location;
        }
        try {
            BufferedImage sourceImage = read(source);
            BufferedImage scaled = scale(sourceImage, width, height);
            sourceImage.flush();
            DynamicTexture dynamicTexture = new DynamicTexture(scaled);
            scaled.flush();
            ResourceLocation location = minecraft.getTextureManager()
                    .getDynamicTextureLocation("pit12-clickgui-icon", dynamicTexture);
            minecraft.getTextureManager().bindTexture(location);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            textures.put(key, new TextureEntry(location, true));
            return location;
        } catch (IOException failure) {
            LOGGER.log(Level.WARNING, "Unable to prepare ClickGUI texture " + source, failure);
            textures.put(key, new TextureEntry(source, false));
            return source;
        }
    }

    void close() {
        for (TextureEntry texture : textures.values()) {
            if (texture.dynamic) {
                minecraft.getTextureManager().deleteTexture(texture.location);
            }
        }
        textures.clear();
    }

    private BufferedImage read(ResourceLocation source) throws IOException {
        try (InputStream input =
                minecraft.getResourceManager().getResource(source).getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException("Unsupported image data");
            }
            return image;
        }
    }

    private static BufferedImage scale(BufferedImage source, int width, int height) {
        BufferedImage current = source;
        boolean ownsCurrent = false;
        while (current.getWidth() != width || current.getHeight() != height) {
            int nextWidth = Math.max(width, current.getWidth() / 2);
            int nextHeight = Math.max(height, current.getHeight() / 2);
            BufferedImage next =
                    new BufferedImage(nextWidth, nextHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = next.createGraphics();
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            graphics.drawImage(current, 0, 0, nextWidth, nextHeight, null);
            graphics.dispose();
            if (ownsCurrent) {
                current.flush();
            }
            current = next;
            ownsCurrent = true;
        }
        if (ownsCurrent) {
            return current;
        }
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private static final class TextureKey {
        private final ResourceLocation source;
        private final int width;
        private final int height;

        private TextureKey(ResourceLocation source, int width, int height) {
            this.source = source;
            this.width = width;
            this.height = height;
        }

        @Override
        public boolean equals(Object candidate) {
            if (this == candidate) {
                return true;
            }
            if (!(candidate instanceof TextureKey)) {
                return false;
            }
            TextureKey other = (TextureKey) candidate;
            return width == other.width && height == other.height && source.equals(other.source);
        }

        @Override
        public int hashCode() {
            int result = source.hashCode();
            result = 31 * result + width;
            return 31 * result + height;
        }
    }
    private static final class TextureEntry {
        private final ResourceLocation location;
        private final boolean dynamic;

        private TextureEntry(ResourceLocation location, boolean dynamic) {
            this.location = location;
            this.dynamic = dynamic;
        }
    }
}
