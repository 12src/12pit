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
package pit12.feature.ringhelper;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.BlockPos;
import org.lwjgl.opengl.GL11;
import pit12.shared.rendering.TextureState;

final class RingRenderer {
    private final TextureState textures;
    private int displayList;
    private long compiledRevision = -1;

    RingRenderer(TextureState textures) {
        this.textures = textures;
    }

    void render(Minecraft minecraft, List<BlockPos> blocks, int color, long revision) {
        if (minecraft.getRenderViewEntity() == null) {
            return;
        }
        if (displayList == 0) {
            displayList = GLAllocation.generateDisplayLists(1);
        }
        if (revision != compiledRevision) {
            GL11.glNewList(displayList, GL11.GL_COMPILE);
            try {
                draw(blocks);
            } finally {
                GL11.glEndList();
            }
            compiledRevision = revision;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        textures.begin();
        GlStateManager.pushMatrix();
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_COLOR_MATERIAL);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE,
                    GL11.GL_ZERO);
            GL11.glLineWidth(1.0F);
            GL11.glColor4f((color >>> 16 & 255) / 255F, (color >>> 8 & 255) / 255F,
                    (color & 255) / 255F, (color >>> 24) / 255F);
            RenderManager camera = minecraft.getRenderManager();
            GlStateManager.translate(-camera.viewerPosX, -camera.viewerPosY, -camera.viewerPosZ);
            GlStateManager.callList(displayList);
        } finally {
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            textures.end();
            GlStateManager.resetColor();
        }
    }

    private static void draw(List<BlockPos> blocks) {
        GL11.glBegin(GL11.GL_LINES);
        try {
            for (BlockPos position : blocks) {
                double x = position.getX();
                double bottomY = position.getY() + 1.0D;
                double z = position.getZ();
                double maxX = x + 1.0D;
                double topY = bottomY + 2.0D;
                double maxZ = z + 1.0D;
                for (int level = 0; level <= 2; level++) {
                    double y = bottomY + level;
                    GL11.glVertex3d(x, y, z);
                    GL11.glVertex3d(maxX, y, z);
                    GL11.glVertex3d(maxX, y, z);
                    GL11.glVertex3d(maxX, y, maxZ);
                    GL11.glVertex3d(maxX, y, maxZ);
                    GL11.glVertex3d(x, y, maxZ);
                    GL11.glVertex3d(x, y, maxZ);
                    GL11.glVertex3d(x, y, z);
                }
                GL11.glVertex3d(x, bottomY, z);
                GL11.glVertex3d(x, topY, z);
                GL11.glVertex3d(maxX, bottomY, z);
                GL11.glVertex3d(maxX, topY, z);
                GL11.glVertex3d(maxX, bottomY, maxZ);
                GL11.glVertex3d(maxX, topY, maxZ);
                GL11.glVertex3d(x, bottomY, maxZ);
                GL11.glVertex3d(x, topY, maxZ);
            }
        } finally {
            GL11.glEnd();
        }
    }

    void clear() {
        if (displayList != 0) {
            GLAllocation.deleteDisplayLists(displayList);
            displayList = 0;
        }
        compiledRevision = -1;
    }
}
