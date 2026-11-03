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
package pit12.feature.render.focus;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import pit12.shared.rendering.RenderStateSnapshot;

public final class FocusRenderState {
    public static float opacity = 1.0F;
    public static boolean renderingEquipment;
    private static float glintScale = 1.0F;
    private static final List<FocusRenderState> DEPTH_OWNERS = new ArrayList<>();
    private static FocusRenderState current;
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private final RenderStateSnapshot state = new RenderStateSnapshot();
    private FocusDepthBuffer depthBuffer;
    private FocusRenderState previous;
    private int depthFunction;
    private float previousOpacity;
    private float previousGlintScale;

    public void begin(float alpha) {
        state.capture();
        previous = current;
        current = this;
        depthFunction = GlStateManager.depthState.depthFunc;
        previousOpacity = opacity;
        previousGlintScale = glintScale;
        opacity = alpha;
        glintScale = (float) Math.sqrt(alpha);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.depthMask(false);
        GL11.glAlphaFunc(GlStateManager.alphaState.func, GlStateManager.alphaState.ref * alpha);
        GlStateManager.resetColor();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void end() {
        try {
            if (depthBuffer != null)
                depthBuffer.restore();
        } finally {
            current = previous;
            opacity = previousOpacity;
            glintScale = previousGlintScale;
            GlStateManager.depthFunc(depthFunction);
            GlStateManager.resetColor();
            state.restore();
            GL11.glAlphaFunc(GlStateManager.alphaState.func,
                    GlStateManager.alphaState.ref * opacity);
        }
    }

    public static void saveDepthBuffer() {
        if (current.depthBuffer == null) {
            current.depthBuffer = new FocusDepthBuffer();
            DEPTH_OWNERS.add(current);
        }
        current.depthBuffer.capture();
    }

    public static void releaseDepthBuffers() {
        for (FocusRenderState owner : DEPTH_OWNERS) {
            owner.depthBuffer.delete();
            owner.depthBuffer = null;
        }
        DEPTH_OWNERS.clear();
    }

    public static void color(float red, float green, float blue, float alpha) {
        if (opacity < 1.0F && additiveGlint()) {
            red *= glintScale;
            green *= glintScale;
            blue *= glintScale;
        }
        // Keep Minecraft's cached color unscaled so nested state restores don't fade it twice.
        GL11.glColor4f(red, green, blue, alpha * opacity);
    }

    public static int glintColor(int color) {
        if (opacity == 1.0F || !additiveGlint())
            return color;
        // Glint uses SRC_COLOR, so scaling RGB by sqrt(opacity) scales its contribution once.
        return color & 0xFF000000 | (int) ((color >> 16 & 255) * glintScale) << 16
                | (int) ((color >> 8 & 255) * glintScale) << 8 | (int) ((color & 255) * glintScale);
    }

    public static int vertexColor(int color) {
        if (opacity == 1.0F)
            return color;
        if (!LITTLE_ENDIAN)
            color = Integer.reverseBytes(color);
        color = glintColor(color & 0xFFFFFF | (int) ((color >>> 24) * opacity) << 24);
        return LITTLE_ENDIAN ? color : Integer.reverseBytes(color);
    }

    private static boolean additiveGlint() {
        return GlStateManager.blendState.srcFactor == GL11.GL_SRC_COLOR
                && GlStateManager.blendState.dstFactor == GL11.GL_ONE;
    }
}
