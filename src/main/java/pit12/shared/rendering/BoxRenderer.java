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

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public final class BoxRenderer {
    private final List<Box> boxes = new ArrayList<>();
    private Frustum frustum;
    private Tessellator tessellator;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private int count;
    private boolean fills;
    private boolean outlines;

    public void beginFrame(double cameraX, double cameraY, double cameraZ) {
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        count = 0;
        fills = false;
        outlines = false;
        if (frustum == null) {
            frustum = new Frustum(ClippingHelperImpl.getInstance());
        } else {
            ClippingHelperImpl.getInstance();
        }
        frustum.setPosition(cameraX, cameraY, cameraZ);
    }

    public void add(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            int color, float fillOpacity, float outlineOpacity) {
        int alpha = color >>> 24;
        int fillAlpha = Math.round(alpha * fillOpacity);
        int outlineAlpha = Math.round(alpha * outlineOpacity);
        if ((fillAlpha == 0 && outlineAlpha == 0)
                || !frustum.isBoxInFrustum(minX, minY, minZ, maxX, maxY, maxZ)) {
            return;
        }
        if (count == boxes.size()) {
            boxes.add(new Box());
        }
        Box box = boxes.get(count++);
        box.minX = minX - cameraX;
        box.minY = minY - cameraY;
        box.minZ = minZ - cameraZ;
        box.maxX = maxX - cameraX;
        box.maxY = maxY - cameraY;
        box.maxZ = maxZ - cameraZ;
        box.red = (color >>> 16) & 255;
        box.green = (color >>> 8) & 255;
        box.blue = color & 255;
        box.fillAlpha = fillAlpha;
        box.outlineAlpha = outlineAlpha;
        fills |= fillAlpha != 0;
        outlines |= outlineAlpha != 0;
    }

    public void render() {
        if (count == 0) {
            return;
        }
        if (tessellator == null) {
            tessellator = new Tessellator(16384);
        }
        // Raw GL calls let the attribute stack restore state without changing Minecraft's cached flags.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT
                | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            try {
                OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_COLOR_MATERIAL);
                GL11.glDisable(GL11.GL_FOG);
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ONE, GL11.GL_ZERO);
                GL11.glEnable(GL11.GL_CULL_FACE);
                GL11.glCullFace(GL11.GL_BACK);
                GL11.glFrontFace(GL11.GL_CCW);
                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
                GL11.glLineWidth(1.0F);
                if (fills) {
                    draw(GL11.GL_QUADS);
                }
                if (outlines) {
                    draw(GL11.GL_LINES);
                }
            } finally {
                GL11.glPopClientAttrib();
            }
        } finally {
            GL11.glPopAttrib();
            GlStateManager.resetColor();
        }
    }

    public void clear() {
        boxes.clear();
        count = 0;
        fills = false;
        outlines = false;
        frustum = null;
        tessellator = null;
    }

    private void draw(int mode) {
        WorldRenderer vertices = tessellator.getWorldRenderer();
        vertices.begin(mode, DefaultVertexFormats.POSITION_COLOR);
        boolean building = true;
        try {
            for (int index = 0; index < count; index++) {
                Box box = boxes.get(index);
                if (mode == GL11.GL_QUADS && box.fillAlpha != 0) {
                    fill(vertices, box);
                } else if (mode == GL11.GL_LINES && box.outlineAlpha != 0) {
                    outline(vertices, box);
                }
            }
            building = false;
            tessellator.draw();
        } finally {
            if (building) {
                vertices.finishDrawing();
            }
            vertices.reset();
        }
    }

    private static void fill(WorldRenderer vertices, Box box) {
        int alpha = box.fillAlpha;
        vertex(vertices, box, box.minX, box.minY, box.minZ, alpha);
        vertex(vertices, box, box.minX, box.maxY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.maxY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.minY, box.minZ, alpha);
        vertex(vertices, box, box.minX, box.minY, box.maxZ, alpha);
        vertex(vertices, box, box.maxX, box.minY, box.maxZ, alpha);
        vertex(vertices, box, box.maxX, box.maxY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.maxY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.minY, box.minZ, alpha);
        vertex(vertices, box, box.minX, box.minY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.maxY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.maxY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.minY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.maxY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.maxY, box.maxZ, alpha);
        vertex(vertices, box, box.maxX, box.minY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.minY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.minY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.minY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.minY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.maxY, box.minZ, alpha);
        vertex(vertices, box, box.minX, box.maxY, box.maxZ, alpha);
        vertex(vertices, box, box.maxX, box.maxY, box.maxZ, alpha);
        vertex(vertices, box, box.maxX, box.maxY, box.minZ, alpha);
    }

    private static void outline(WorldRenderer vertices, Box box) {
        int alpha = box.outlineAlpha;
        vertex(vertices, box, box.minX, box.minY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.minY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.minY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.minY, box.maxZ, alpha);
        vertex(vertices, box, box.maxX, box.minY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.minY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.minY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.minY, box.minZ, alpha);
        vertex(vertices, box, box.minX, box.maxY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.maxY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.maxY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.maxY, box.maxZ, alpha);
        vertex(vertices, box, box.maxX, box.maxY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.maxY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.maxY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.maxY, box.minZ, alpha);
        vertex(vertices, box, box.minX, box.minY, box.minZ, alpha);
        vertex(vertices, box, box.minX, box.maxY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.minY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.maxY, box.minZ, alpha);
        vertex(vertices, box, box.maxX, box.minY, box.maxZ, alpha);
        vertex(vertices, box, box.maxX, box.maxY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.minY, box.maxZ, alpha);
        vertex(vertices, box, box.minX, box.maxY, box.maxZ, alpha);
    }

    private static void vertex(WorldRenderer vertices, Box box, double x, double y, double z,
            int alpha) {
        vertices.pos(x, y, z).color(box.red, box.green, box.blue, alpha).endVertex();
    }

    private static final class Box {
        double minX;
        double minY;
        double minZ;
        double maxX;
        double maxY;
        double maxZ;
        int red;
        int green;
        int blue;
        int fillAlpha;
        int outlineAlpha;
    }
}
