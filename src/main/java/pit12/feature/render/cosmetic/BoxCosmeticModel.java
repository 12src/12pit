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

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

public final class BoxCosmeticModel implements CosmeticModel {
    private final ResourceLocation texture;
    private final ModelRenderer[] outer;
    private final ModelRenderer[] inner;

    private BoxCosmeticModel(ResourceLocation texture, List<ModelRenderer> outer,
            List<ModelRenderer> inner) {
        this.texture = texture;
        this.outer = outer.toArray(new ModelRenderer[0]);
        this.inner = inner.toArray(new ModelRenderer[0]);
    }

    @Override
    public void render() {
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        for (ModelRenderer part : outer) {
            part.render(0.0625F);
        }
        for (ModelRenderer part : inner) {
            part.render(0.0625F);
        }
    }

    @Override
    public void dispose() {}

    public static final class Builder extends ModelBase {
        private final List<ModelRenderer> outer = new ArrayList<>();
        private final List<ModelRenderer> inner = new ArrayList<>();

        public void textureSize(int width, int height) {
            textureWidth = width;
            textureHeight = height;
        }

        public void add(boolean innerPart, int u, int v, float[] origin, int[] size,
                float[] rotationPoint, float[] rotation) {
            ModelRenderer part = new ModelRenderer(this, u, v);
            part.textureWidth = textureWidth;
            part.textureHeight = textureHeight;
            part.setRotationPoint(rotationPoint[0], rotationPoint[1], rotationPoint[2]);
            part.rotateAngleX = rotation[0];
            part.rotateAngleY = rotation[1];
            part.rotateAngleZ = rotation[2];
            part.addBox(origin[0], origin[1], origin[2], size[0], size[1], size[2]);
            if (innerPart) {
                inner.add(part);
            } else {
                outer.add(part);
            }
        }

        public BoxCosmeticModel build(ResourceLocation texture) {
            return new BoxCosmeticModel(texture, outer, inner);
        }

        @Override
        public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                float netHeadYaw, float headPitch, float scale) {}
    }
}
