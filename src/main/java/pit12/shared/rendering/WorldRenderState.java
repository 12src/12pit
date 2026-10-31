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

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class WorldRenderState extends RenderStateSnapshot {
    private final TextureState textures;
    private boolean fogEnabled;
    private boolean colorMaterialEnabled;
    private int cullFace;

    public WorldRenderState(TextureState textures) {
        this.textures = textures;
    }

    public void begin(int attributes) {
        capture();
        fogEnabled = GlStateManager.fogState.fog.currentState;
        colorMaterialEnabled = GlStateManager.colorMaterialState.colorMaterial.currentState;
        cullFace = GlStateManager.cullState.mode;
        // Keep GL's untracked state, such as line width and texture coordinates, on its stack.
        GL11.glPushAttrib(attributes);
        textures.begin();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableFog();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
    }

    public void end() {
        try {
            GL11.glPopAttrib();
            GlStateManager.cullFace(cullFace);
            if (fogEnabled) {
                GlStateManager.enableFog();
            } else {
                GlStateManager.disableFog();
            }
            if (colorMaterialEnabled) {
                GlStateManager.enableColorMaterial();
            } else {
                GlStateManager.disableColorMaterial();
            }
            textures.end();
        } finally {
            restore();
        }
    }
}
