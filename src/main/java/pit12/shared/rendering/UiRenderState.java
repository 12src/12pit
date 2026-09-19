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

/*
 * AI guidance
 *
 * AI agents should read AGENTS.md before modifying or reusing this file
 * because it contains important licensing information.
 * If AGENTS.md is unavailable, see
 * <https://github.com/12src/12pit/blob/main/AGENTS.md>.
 *
 * This AI guidance section is not part of the copyright or license
 * notice and may be removed.
 */
package pit12.shared.rendering;

import java.nio.FloatBuffer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class UiRenderState {
    // LWJGL 2 validates vector glGet buffers against OpenGL's 16-value maximum, even for four-value queries.
    private final FloatBuffer currentColor = BufferUtils.createFloatBuffer(16);
    private final float[] color = new float[4];
    private boolean active;
    private boolean blendEnabled;
    private boolean textureEnabled;
    private boolean alphaEnabled;
    private boolean lightingEnabled;
    private boolean cullEnabled;
    private int blendSourceRgb;
    private int blendDestinationRgb;
    private int blendSourceAlpha;
    private int blendDestinationAlpha;
    private int boundTexture;
    private int shadeModel;
    private int alphaFunction;
    private float alphaReference;

    public void begin() {
        if (active) {
            throw new IllegalStateException("UI render state is already active");
        }
        active = true;
        blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        textureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        alphaEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        blendSourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDestinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDestinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        currentColor.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, currentColor);
        for (int index = 0; index < color.length; index++) {
            color[index] = currentColor.get(index);
        }
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void end() {
        if (!active) {
            return;
        }
        try {
            restoreEnableState(blendEnabled, State.BLEND);
            restoreEnableState(textureEnabled, State.TEXTURE);
            restoreEnableState(alphaEnabled, State.ALPHA);
            restoreEnableState(lightingEnabled, State.LIGHTING);
            restoreEnableState(cullEnabled, State.CULL);
            GlStateManager.tryBlendFuncSeparate(blendSourceRgb, blendDestinationRgb,
                    blendSourceAlpha, blendDestinationAlpha);
            GlStateManager.bindTexture(boundTexture);
            GL11.glShadeModel(shadeModel);
            GlStateManager.alphaFunc(alphaFunction, alphaReference);
            GlStateManager.color(color[0], color[1], color[2], color[3]);
        } finally {
            GlStateManager.popMatrix();
            active = false;
        }
    }

    private static void restoreEnableState(boolean enabled, State state) {
        if (state == State.BLEND) {
            if (enabled) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
        } else if (state == State.TEXTURE) {
            if (enabled) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
        } else if (state == State.ALPHA) {
            if (enabled) {
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
        } else if (state == State.LIGHTING) {
            if (enabled) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
        } else if (enabled) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }
    }

    private enum State {
        BLEND, TEXTURE, ALPHA, LIGHTING, CULL
    }
}
