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
package pit12.feature.render.focus.mixin;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.render.focus.FocusRenderPass;

@Mixin(value = RenderGlobal.class, priority = -1)
public abstract class OptiFineRenderGlobalRedirect {
    @Unique
    private static final Consumer<Entity> pit12$focusShaderEntity =
            OptiFineShadersMixin::pit12$nextEntity;

    @Redirect(method = "renderEntities", at = @At(value = "INVOKE",
            target = "Lnet/optifine/shaders/Shaders;endBlockEntities()V", remap = false))
    private void pit12$finishShaderPlayerPass() {
        if (FocusRenderPass.current == null) {
            OptiFineShadersMixin.pit12$endBlockEntities();
            return;
        }
        Minecraft.getMinecraft().entityRenderer.enableLightmap();
        OptiFineShadersMixin.pit12$setBlockEntityId(null);
        try {
            FocusRenderPass.current.draw(pit12$focusShaderEntity,
                    OptiFineShadersMixin.pit12$isShadowPass());
        } finally {
            OptiFineShadersMixin.pit12$setEntityId(null);
            // This also restores the normal and specular textures after player rendering.
            OptiFineShadersMixin.pit12$endBlockEntities();
        }
    }
}
