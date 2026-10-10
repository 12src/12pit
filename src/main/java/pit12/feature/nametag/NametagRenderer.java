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
package pit12.feature.nametag;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

final class NametagRenderer {
    void render(Minecraft minecraft, EntityPlayer player, NametagText text, double x, double y,
            double z, double distanceSquared, int upwardOffset) {
        FontRenderer font = minecraft.fontRendererObj;
        RenderManager renderManager = minecraft.getRenderManager();
        boolean sneaking = player.isSneaking();
        double nameY = y + player.height + 0.5D - (sneaking ? 0.25D : 0.0D);
        if (!sneaking && distanceSquared < 100.0D
                && player.getWorldScoreboard().getObjectiveInDisplaySlot(2) != null) {
            // A below-name score raises the player's name by one line.
            nameY += font.FONT_HEIGHT * 1.15F * 0.02666667F;
        }
        int heldItemY = text.leggings == null ? 0 : -font.FONT_HEIGHT - 2;
        boolean alphaEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean textureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        // Raw GL changes leave Minecraft's cached flags unchanged.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, nameY + upwardOffset * 0.01D, z);
            GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager
                    .rotate(minecraft.gameSettings.thirdPersonView == 2 ? -renderManager.playerViewX
                            : renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-0.02666667F, -0.02666667F, 0.02666667F);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE,
                    GL11.GL_ZERO);
            GlStateManager.enableAlpha();
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            if (sneaking) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            } else {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer vertices = tessellator.getWorldRenderer();
            vertices.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            if (text.leggings != null) {
                background(vertices, text.leggingsWidth, 0, font.FONT_HEIGHT);
            }
            if (text.heldItem != null) {
                background(vertices, text.heldItemWidth, heldItemY, font.FONT_HEIGHT);
            }
            tessellator.draw();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GlStateManager.enableTexture2D();
            drawText(font, text, heldItemY, 0x20FFFFFF);
            if (!sneaking) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
                drawText(font, text, heldItemY, 0xFFFFFFFF);
            }
        } finally {
            // FontRenderer changes cached alpha, texture and color state.
            GlStateManager.bindTexture(boundTexture);
            if (textureEnabled) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            if (alphaEnabled) {
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
            GL11.glPopAttrib();
            GlStateManager.resetColor();
            GlStateManager.popMatrix();
        }
    }

    private static void drawText(FontRenderer font, NametagText text, int heldItemY, int color) {
        if (text.leggings != null) {
            font.drawString(text.leggings, -text.leggingsWidth / 2, 0, color);
        }
        if (text.heldItem != null) {
            font.drawString(text.heldItem, -text.heldItemWidth / 2, heldItemY, color);
        }
    }

    private static void background(WorldRenderer vertices, int width, int y, int height) {
        int left = -width / 2 - 1;
        int right = left + width + 2;
        vertices.pos(left, y - 1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        vertices.pos(left, y + height, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        vertices.pos(right, y + height, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        vertices.pos(right, y - 1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
    }
}
