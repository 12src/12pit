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
package pit12.feature.render.playerlist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import pit12.shared.build.BuildConfig;

final class PlayerListArrows {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(BuildConfig.MOD_ID, "textures/gui/playerlist/arrow.png");
    private final Minecraft minecraft;
    private final Tessellator tessellator = Tessellator.getInstance();
    private final WorldRenderer vertices = tessellator.getWorldRenderer();
    private boolean drawing;
    private float pixelScale;
    private int pixelSize;
    private double inverseScale;
    private double centerX;
    private double minimum;
    private double maximum;

    PlayerListArrows(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    void begin(float pixelScale, int x, int size) {
        this.pixelScale = pixelScale;
        pixelSize = Math.max(1, Math.round(size * pixelScale));
        inverseScale = 1.0D / pixelScale;
        centerX = (Math.round(x * pixelScale) + pixelSize / 2.0D) * inverseScale;
        // Keep the old half-pixel offset for odd arrow sizes.
        minimum = (-pixelSize / 2) * inverseScale;
        maximum = minimum + pixelSize * inverseScale;
    }

    void add(int y, int color, double sine, double cosine) {
        if (!drawing) {
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ZERO);
            minecraft.getTextureManager().bindTexture(TEXTURE);
            vertices.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            drawing = true;
        }
        double centerY = (Math.round(y * pixelScale) + pixelSize / 2.0D) * inverseScale;
        int red = color >>> 16 & 0xFF;
        int green = color >>> 8 & 0xFF;
        int blue = color & 0xFF;
        int alpha = color >>> 24;
        vertices.pos(centerX + minimum * cosine - maximum * sine,
                centerY + minimum * sine + maximum * cosine, 0.0D).tex(0.0D, 1.0D)
                .color(red, green, blue, alpha).endVertex();
        vertices.pos(centerX + maximum * cosine - maximum * sine,
                centerY + maximum * sine + maximum * cosine, 0.0D).tex(1.0D, 1.0D)
                .color(red, green, blue, alpha).endVertex();
        vertices.pos(centerX + maximum * cosine - minimum * sine,
                centerY + maximum * sine + minimum * cosine, 0.0D).tex(1.0D, 0.0D)
                .color(red, green, blue, alpha).endVertex();
        vertices.pos(centerX + minimum * cosine - minimum * sine,
                centerY + minimum * sine + minimum * cosine, 0.0D).tex(0.0D, 0.0D)
                .color(red, green, blue, alpha).endVertex();
    }

    void draw() {
        if (drawing) {
            try {
                tessellator.draw();
            } finally {
                drawing = false;
            }
        }
    }
}
