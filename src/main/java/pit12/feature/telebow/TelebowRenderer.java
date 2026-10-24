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
package pit12.feature.telebow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import pit12.shared.rendering.FontWidths;
import pit12.shared.rendering.TextureState;

final class TelebowRenderer {
    private static final ResourceLocation BEAM_TEXTURE =
            new ResourceLocation("pit12", "textures/telebow/beam.png");
    private static final double[] CIRCLE = new double[48];
    static {
        for (int index = 0; index < CIRCLE.length; index += 2) {
            double angle = index * Math.PI * 2 / CIRCLE.length;
            CIRCLE[index] = Math.cos(angle);
            CIRCLE[index + 1] = Math.sin(angle);
        }
    }
    private final Minecraft minecraft;
    private final TelebowConfig config;
    private final TextureState textures;
    private final Map<TelebowPath, Mesh> meshes = new IdentityHashMap<>();
    private final List<Mesh> visible = new ArrayList<>();
    private final List<TelebowAim.Target> visibleAims = new ArrayList<>();
    private ClippingHelper clipping;
    private Frustum frustum;

    TelebowRenderer(Minecraft minecraft, TelebowConfig config, TextureState textures) {
        this.minecraft = minecraft;
        this.config = config;
        this.textures = textures;
    }

    void render(Collection<TelebowPath> paths, Collection<TelebowAim.Target> aims, long now) {
        if (minecraft.getRenderViewEntity() == null)
            return;
        long hold = config.holdTime.get() * 1_000_000L;
        long fade = config.fadeTime.get() * 1_000_000L;
        boolean names = config.showNames.get();
        FontRenderer font = minecraft.fontRendererObj;
        RenderManager camera = minecraft.getRenderManager();
        if (frustum == null) {
            // Vanilla refreshes this shared helper before every world pass.
            clipping = ClippingHelperImpl.getInstance();
            frustum = new Frustum(clipping);
        }
        frustum.setPosition(camera.viewerPosX, camera.viewerPosY, camera.viewerPosZ);
        double pixelMargin =
                4.0 / Math.min(Math.abs(clipping.projectionMatrix[0]) * minecraft.displayWidth,
                        Math.abs(clipping.projectionMatrix[5]) * minecraft.displayHeight);
        boolean unicode = font.getUnicodeFlag();
        boolean drawNames = false;
        boolean drawBeams = false;
        for (TelebowPath path : paths) {
            TelebowTrajectory trajectory = path.trajectory;
            float alpha = (path.color >>> 24) / 255F * path.opacity(now, hold, fade);
            if (alpha <= 0 || trajectory.count < 2 && !trajectory.hasImpact)
                continue;
            Mesh mesh = meshes.get(path);
            if (mesh == null) {
                mesh = new Mesh(path);
                meshes.put(path, mesh);
            }
            if (mesh.boundsRevision != path.revision) {
                updateBounds(mesh);
                mesh.boundsRevision = path.revision;
            }
            AxisAlignedBB bounds = mesh.bounds;
            // Leave room for the two-pixel lines at the edge of the screen.
            double margin = Math.max(1,
                    (Math.max(Math.abs(bounds.minX - camera.viewerPosX),
                            Math.abs(bounds.maxX - camera.viewerPosX))
                            + Math.max(Math.abs(bounds.minY - camera.viewerPosY),
                                    Math.abs(bounds.maxY - camera.viewerPosY))
                            + Math.max(Math.abs(bounds.minZ - camera.viewerPosZ),
                                    Math.abs(bounds.maxZ - camera.viewerPosZ)))
                            * pixelMargin);
            mesh.geometryVisible = frustum.isBoxInFrustum(bounds.minX - margin,
                    bounds.minY - margin, bounds.minZ - margin, bounds.maxX + margin,
                    bounds.maxY + margin, bounds.maxZ + margin);
            mesh.nameVisible = false;
            if (names && trajectory.hasImpact && Math.round(alpha * 255) >= 4) {
                if (mesh.nameWidth < 0 || mesh.unicode != unicode) {
                    mesh.nameWidth = FontWidths.measure(font, path.displayName);
                    mesh.unicode = unicode;
                }
                int offset = (trajectory.count - 1) * 3;
                double x = trajectory.points[offset];
                double y = trajectory.points[offset + 1];
                double z = trajectory.points[offset + 2];
                double dx = x - camera.viewerPosX;
                double dy = y - camera.viewerPosY;
                double dz = z - camera.viewerPosZ;
                mesh.nameScale = 0.02666667F * (float) Math.max(1,
                        Math.min(4, Math.sqrt(dx * dx + dy * dy + dz * dz) / 32));
                double halfWidth = mesh.nameWidth / 2 + 3;
                double height = font.FONT_HEIGHT + 2;
                double radius = mesh.nameScale * Math.sqrt(halfWidth * halfWidth + height * height)
                        + margin;
                mesh.nameVisible = frustum.isBoxInFrustum(x - radius, y + 1.5 - radius, z - radius,
                        x + radius, y + 1.5 + radius, z + radius);
            }
            if (mesh.geometryVisible || mesh.nameVisible) {
                mesh.alpha = alpha;
                visible.add(mesh);
                drawNames |= mesh.nameVisible;
                drawBeams |= mesh.geometryVisible && trajectory.hasImpact;
            }
        }
        boolean aimNames = config.aimNames.get();
        if (!aims.isEmpty() && minecraft.getRenderViewEntity() == minecraft.thePlayer) {
            double pixelScale =
                    2.0 / (Math.abs(clipping.projectionMatrix[5]) * minecraft.displayHeight)
                            * new ScaledResolution(minecraft).getScaleFactor();
            for (TelebowAim.Target target : aims) {
                float alpha = (target.color >>> 24) / 255F * target.path.opacity(now, hold, fade);
                if (!target.renderable || alpha <= 0)
                    continue;
                if (aimNames && (target.nameWidth < 0 || target.unicode != unicode)) {
                    target.nameWidth = FontWidths.measure(font, target.path.displayName);
                    target.unicode = unicode;
                }
                target.alpha = alpha;
                target.scale = (float) (target.distance * pixelScale);
                double radius =
                        target.scale * (aimNames ? Math.max(20, target.nameWidth / 2 + 3) : 6);
                if (frustum.isBoxInFrustum(target.x - radius, target.y - radius, target.z - radius,
                        target.x + radius, target.y + radius, target.z + radius))
                    visibleAims.add(target);
            }
        }
        drawNames |= aimNames && !visibleAims.isEmpty();
        if (visible.isEmpty() && visibleAims.isEmpty())
            return;
        boolean alphaEnabled = drawNames && GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean textureEnabled = drawNames && GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        textures.begin();
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.pushMatrix();
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_COLOR_MATERIAL);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE,
                    GL11.GL_ZERO);
            GL11.glLineWidth(2F);
            GlStateManager.translate(-camera.viewerPosX, -camera.viewerPosY, -camera.viewerPosZ);
            double angle = Math.toRadians(camera.playerViewY);
            double beamX = Math.cos(angle) * 0.22;
            double beamZ = Math.sin(angle) * 0.22;
            if (drawBeams) {
                textures.setEnabled(true);
                minecraft.getTextureManager().bindTexture(BEAM_TEXTURE);
                GL11.glBegin(GL11.GL_QUADS);
                try {
                    for (Mesh mesh : visible) {
                        if (!mesh.geometryVisible || !mesh.path.trajectory.hasImpact)
                            continue;
                        int color = mesh.path.color;
                        GL11.glColor4f((color >>> 16 & 255) / 255F, (color >>> 8 & 255) / 255F,
                                (color & 255) / 255F, mesh.alpha * 0.75F);
                        drawBeam(mesh.path, beamX, beamZ);
                    }
                } finally {
                    GL11.glEnd();
                }
            }
            textures.setEnabled(false);
            for (Mesh mesh : visible) {
                TelebowPath path = mesh.path;
                if (mesh.geometryVisible) {
                    if (mesh.revision != path.revision) {
                        if (mesh.lines == 0)
                            mesh.lines = GLAllocation.generateDisplayLists(1);
                        compile(mesh, path);
                        mesh.revision = path.revision;
                    }
                    int color = path.color;
                    GL11.glColor4f((color >>> 16 & 255) / 255F, (color >>> 8 & 255) / 255F,
                            (color & 255) / 255F, mesh.alpha);
                    GlStateManager.callList(mesh.lines);
                }
            }
            for (Mesh mesh : visible) {
                if (mesh.nameVisible) {
                    drawName(mesh.path, mesh.nameWidth, mesh.nameScale, font, camera, mesh.alpha);
                }
            }
            for (TelebowAim.Target target : visibleAims) {
                drawAim(target, aimNames, font, camera);
            }
        } finally {
            visible.clear();
            visibleAims.clear();
            // Keep the cached binding in sync with the state restored by glPopAttrib.
            GlStateManager.bindTexture(boundTexture);
            if (drawNames) {
                if (textureEnabled)
                    GlStateManager.enableTexture2D();
                else
                    GlStateManager.disableTexture2D();
                if (alphaEnabled)
                    GlStateManager.enableAlpha();
                else
                    GlStateManager.disableAlpha();
            }
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            textures.end();
            GlStateManager.resetColor();
        }
    }

    private void drawAim(TelebowAim.Target target, boolean names, FontRenderer font,
            RenderManager camera) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(target.x, target.y, target.z);
            GlStateManager.rotate(-camera.playerViewY, 0, 1, 0);
            GlStateManager.rotate(minecraft.gameSettings.thirdPersonView == 2 ? -camera.playerViewX
                    : camera.playerViewX, 1, 0, 0);
            GlStateManager.scale(-target.scale, -target.scale, target.scale);
            textures.setEnabled(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            int color = target.color;
            float alpha = target.alpha;
            GL11.glColor4f((color >>> 16 & 255) / 255F, (color >>> 8 & 255) / 255F,
                    (color & 255) / 255F, alpha);
            GL11.glBegin(GL11.GL_LINES);
            try {
                GL11.glVertex2f(-5, 0);
                GL11.glVertex2f(-1, 0);
                GL11.glVertex2f(1, 0);
                GL11.glVertex2f(5, 0);
                GL11.glVertex2f(0, -5);
                GL11.glVertex2f(0, -1);
                GL11.glVertex2f(0, 1);
                GL11.glVertex2f(0, 5);
            } finally {
                GL11.glEnd();
            }
            if (names) {
                GlStateManager.translate(0, 9, 0);
                drawLabel(target.path.displayName, target.nameWidth, font, alpha);
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void updateBounds(Mesh mesh) {
        TelebowPath path = mesh.path;
        TelebowTrajectory trajectory = path.trajectory;
        double minX = trajectory.points[0], minY = trajectory.points[1],
                minZ = trajectory.points[2];
        double maxX = minX, maxY = minY, maxZ = minZ;
        for (int index = 1; index < trajectory.count; index++) {
            int offset = index * 3;
            minX = Math.min(minX, trajectory.points[offset]);
            minY = Math.min(minY, trajectory.points[offset + 1]);
            minZ = Math.min(minZ, trajectory.points[offset + 2]);
            maxX = Math.max(maxX, trajectory.points[offset]);
            maxY = Math.max(maxY, trajectory.points[offset + 1]);
            maxZ = Math.max(maxZ, trajectory.points[offset + 2]);
        }
        if (trajectory.hasImpact)
            maxY = Math.max(maxY, beamTop(path));
        mesh.bounds = new AxisAlignedBB(minX - 0.25, minY, minZ - 0.25, maxX + 0.25, maxY + 0.02,
                maxZ + 0.25);
    }

    private static void compile(Mesh mesh, TelebowPath path) {
        TelebowTrajectory trajectory = path.trajectory;
        GL11.glNewList(mesh.lines, GL11.GL_COMPILE);
        try {
            drawLines(path);
            if (trajectory.hasImpact)
                drawLanding(trajectory);
        } finally {
            GL11.glEndList();
        }
    }

    private static void drawLines(TelebowPath path) {
        TelebowTrajectory trajectory = path.trajectory;
        GL11.glBegin(GL11.GL_LINES);
        try {
            for (int index = 1; index < trajectory.count; index++) {
                int offset = index * 3;
                GL11.glVertex3d(trajectory.points[offset - 3], trajectory.points[offset - 2],
                        trajectory.points[offset - 1]);
                GL11.glVertex3d(trajectory.points[offset], trajectory.points[offset + 1],
                        trajectory.points[offset + 2]);
            }
        } finally {
            GL11.glEnd();
        }
    }

    private static void drawLanding(TelebowTrajectory trajectory) {
        int offset = (trajectory.count - 1) * 3;
        double x = trajectory.points[offset];
        double y = trajectory.points[offset + 1] + 0.02;
        double z = trajectory.points[offset + 2];
        GL11.glBegin(GL11.GL_LINE_LOOP);
        try {
            for (int index = 0; index < CIRCLE.length; index += 2) {
                GL11.glVertex3d(x + CIRCLE[index] * 0.25, y, z + CIRCLE[index + 1] * 0.25);
            }
        } finally {
            GL11.glEnd();
        }
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        try {
            GL11.glVertex3d(x, y, z);
            for (int index = 0; index <= CIRCLE.length; index += 2) {
                int point = index % CIRCLE.length;
                GL11.glVertex3d(x + CIRCLE[point] * 0.035, y, z + CIRCLE[point + 1] * 0.035);
            }
        } finally {
            GL11.glEnd();
        }
    }

    private static void drawBeam(TelebowPath path, double rightX, double rightZ) {
        TelebowTrajectory trajectory = path.trajectory;
        int offset = (trajectory.count - 1) * 3;
        double x = trajectory.points[offset];
        double y = trajectory.points[offset + 1];
        double z = trajectory.points[offset + 2];
        double top = beamTop(path);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex3d(x - rightX, y, z - rightZ);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex3d(x - rightX, top, z - rightZ);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex3d(x + rightX, top, z + rightZ);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex3d(x + rightX, y, z + rightZ);
    }

    private static double beamTop(TelebowPath path) {
        return Math.max(path.trajectory.points[(path.trajectory.count - 1) * 3 + 1] + 64,
                path.arrow.worldObj.getActualHeight());
    }

    private void drawName(TelebowPath path, int width, float scale, FontRenderer font,
            RenderManager camera, float alpha) {
        TelebowTrajectory trajectory = path.trajectory;
        int offset = (trajectory.count - 1) * 3;
        double x = trajectory.points[offset];
        double y = trajectory.points[offset + 1];
        double z = trajectory.points[offset + 2];
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y + 1.5, z);
            GlStateManager.rotate(-camera.playerViewY, 0, 1, 0);
            GlStateManager.rotate(minecraft.gameSettings.thirdPersonView == 2 ? -camera.playerViewX
                    : camera.playerViewX, 1, 0, 0);
            GlStateManager.scale(-scale, -scale, scale);
            drawLabel(path.displayName, width, font, alpha);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void drawLabel(String name, int width, FontRenderer font, float alpha) {
        textures.setEnabled(false);
        GL11.glColor4f(0, 0, 0, alpha * 0.5F);
        GL11.glBegin(GL11.GL_QUADS);
        try {
            GL11.glVertex3d(-width / 2 - 3, -2, 0);
            GL11.glVertex3d(-width / 2 - 3, font.FONT_HEIGHT + 2, 0);
            GL11.glVertex3d(width / 2 + 3, font.FONT_HEIGHT + 2, 0);
            GL11.glVertex3d(width / 2 + 3, -2, 0);
        } finally {
            GL11.glEnd();
        }
        textures.setEnabled(true);
        // FontRenderer uses this cache for text decorations too.
        GlStateManager.enableTexture2D();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.001F);
        GlStateManager.enableAlpha();
        GlStateManager.resetColor();
        font.drawString(name, -width / 2, 0, Math.round(alpha * 255) << 24 | 0xFFFFFF);
    }

    void remove(TelebowPath path) {
        Mesh mesh = meshes.remove(path);
        if (mesh != null)
            delete(mesh);
    }

    void clear() {
        for (Mesh mesh : meshes.values()) {
            delete(mesh);
        }
        meshes.clear();
        visible.clear();
        visibleAims.clear();
    }

    private static void delete(Mesh mesh) {
        if (mesh.lines != 0)
            GLAllocation.deleteDisplayLists(mesh.lines);
    }

    private static final class Mesh {
        final TelebowPath path;
        int lines;
        long revision = -1;
        long boundsRevision = -1;
        AxisAlignedBB bounds;
        boolean geometryVisible;
        boolean nameVisible;
        float nameScale;
        float alpha;
        int nameWidth = -1;
        boolean unicode;

        Mesh(TelebowPath path) {
            this.path = path;
        }
    }
}
