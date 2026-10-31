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

import java.nio.FloatBuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class RenderStateSnapshot {
    private boolean blendEnabled;
    private boolean textureEnabled;
    private boolean alphaEnabled;
    private boolean lightingEnabled;
    private boolean cullEnabled;
    private boolean depthEnabled;
    private boolean depthMask;
    private int alphaFunction;
    private float alphaReference;
    private int blendSourceRgb;
    private int blendDestinationRgb;
    private int blendSourceAlpha;
    private int blendDestinationAlpha;
    private int textureUnit;
    private int boundTexture;
    private float red;
    private float green;
    private float blue;
    private float alpha;
    private FloatBuffer currentColor;

    public void beginUi() {
        capture();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void capture() {
        blendEnabled = GlStateManager.blendState.blend.currentState;
        alphaEnabled = GlStateManager.alphaState.alphaTest.currentState;
        lightingEnabled = GlStateManager.lightingState.currentState;
        cullEnabled = GlStateManager.cullState.cullFace.currentState;
        depthEnabled = GlStateManager.depthState.depthTest.currentState;
        depthMask = GlStateManager.depthState.maskEnabled;
        alphaFunction = GlStateManager.alphaState.func;
        alphaReference = GlStateManager.alphaState.ref;
        blendSourceRgb = GlStateManager.blendState.srcFactor;
        blendDestinationRgb = GlStateManager.blendState.dstFactor;
        blendSourceAlpha = GlStateManager.blendState.srcFactorAlpha;
        blendDestinationAlpha = GlStateManager.blendState.dstFactorAlpha;
        textureUnit = GlStateManager.activeTextureUnit;
        textureEnabled = GlStateManager.textureState[textureUnit].texture2DState.currentState;
        boundTexture = GlStateManager.textureState[textureUnit].textureName;
        red = GlStateManager.colorState.red;
        green = GlStateManager.colorState.green;
        blue = GlStateManager.colorState.blue;
        alpha = GlStateManager.colorState.alpha;
        // resetColor() invalidates the cache while leaving the GL color in place.
        if (red < 0.0F || green < 0.0F || blue < 0.0F || alpha < 0.0F) {
            if (currentColor == null) {
                // LWJGL 2 requires 16 slots for glGetFloat.
                currentColor = BufferUtils.createFloatBuffer(16);
            }
            currentColor.clear();
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, currentColor);
            red = currentColor.get(0);
            green = currentColor.get(1);
            blue = currentColor.get(2);
            alpha = currentColor.get(3);
        }
    }

    public void restore() {
        if (blendEnabled) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }
        if (alphaEnabled) {
            GlStateManager.enableAlpha();
        } else {
            GlStateManager.disableAlpha();
        }
        if (lightingEnabled) {
            GlStateManager.enableLighting();
        } else {
            GlStateManager.disableLighting();
        }
        if (cullEnabled) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }
        if (depthEnabled) {
            GlStateManager.enableDepth();
        } else {
            GlStateManager.disableDepth();
        }
        GlStateManager.depthMask(depthMask);
        GlStateManager.alphaFunc(alphaFunction, alphaReference);
        GlStateManager.tryBlendFuncSeparate(blendSourceRgb, blendDestinationRgb, blendSourceAlpha,
                blendDestinationAlpha);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + textureUnit);
        if (textureEnabled) {
            GlStateManager.enableTexture2D();
        } else {
            GlStateManager.disableTexture2D();
        }
        GlStateManager.bindTexture(boundTexture);
        GlStateManager.color(red, green, blue, alpha);
    }
}
