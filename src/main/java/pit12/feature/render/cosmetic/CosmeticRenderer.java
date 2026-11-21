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
package pit12.feature.render.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import pit12.shared.rendering.RenderStateSnapshot;

public final class CosmeticRenderer {
    private static final float RADIANS_TO_DEGREES = 57.29578F;
    private static final float HEAD_TOP = -8.0F;
    private static final float INNER_RED = 0.95F;
    private static final float INNER_GREEN = 0.55F;
    private static final float INNER_BLUE = 0.62F;
    private final Minecraft minecraft;
    private final CosmeticConfig config;
    private final CosmeticModel model;
    private final RenderStateSnapshot state = new RenderStateSnapshot();

    public CosmeticRenderer(Minecraft minecraft, CosmeticConfig config, CosmeticModel model) {
        this.minecraft = minecraft;
        this.config = config;
        this.model = model;
    }

    public void render(EntityPlayer player, ModelBiped model, float scale) {
        if (!config.enabled() || !showsOn(player) || !inRange(player)) {
            return;
        }
        ModelRenderer head = model.bipedHead;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(head.rotationPointX * scale, head.rotationPointY * scale,
                    head.rotationPointZ * scale);
            rotate(head.rotateAngleZ, 0.0F, 0.0F, 1.0F);
            rotate(head.rotateAngleY, 0.0F, 1.0F, 0.0F);
            rotate(head.rotateAngleX, 1.0F, 0.0F, 0.0F);
            draw(scale);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private boolean showsOn(EntityPlayer player) {
        if (player.isInvisible() || player.worldObj != minecraft.theWorld) {
            return false;
        }
        int target = config.target();
        if (player == minecraft.thePlayer) {
            return target != CosmeticConfig.OTHERS;
        }
        return target != CosmeticConfig.MYSELF;
    }

    private boolean inRange(EntityPlayer player) {
        Entity view = minecraft.getRenderViewEntity();
        if (view == null) {
            return false;
        }
        int distance = config.distance();
        return player.getDistanceSqToEntity(view) <= distance * distance;
    }

    private void draw(float scale) {
        int color = config.color();
        float size = config.size() / 100.0F;
        float top = HEAD_TOP * scale;
        state.capture();
        try {
            GlStateManager.translate(0.0F, top, 0.0F);
            GlStateManager.scale(size, size, size);
            GlStateManager.translate(0.0F, -top, 0.0F);
            minecraft.getTextureManager().bindTexture(model.texture());
            GlStateManager.color(((color >>> 16) & 255) / 255.0F, ((color >>> 8) & 255) / 255.0F,
                    (color & 255) / 255.0F, 1.0F);
            model.renderOuter(scale);
            GlStateManager.color(INNER_RED, INNER_GREEN, INNER_BLUE, 1.0F);
            model.renderInner(scale);
        } finally {
            state.restore();
        }
    }

    private static void rotate(float radians, float x, float y, float z) {
        if (radians != 0.0F) {
            GlStateManager.rotate(radians * RADIANS_TO_DEGREES, x, y, z);
        }
    }
}
