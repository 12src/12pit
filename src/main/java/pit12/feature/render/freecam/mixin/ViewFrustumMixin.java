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
package pit12.feature.render.freecam.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.render.freecam.FreeCameraBinding;
import pit12.feature.render.freecam.FreeCameraFeature;

@Mixin(ViewFrustum.class)
public abstract class ViewFrustumMixin {
    @Unique
    private boolean pit12$wasFreeCameraActive;
    @Unique
    private boolean pit12$rebuildCameraChunks;

    @Inject(method = "updateChunkPositions", at = @At("HEAD"))
    private void pit12$trackFreeCamera(CallbackInfo callback) {
        FreeCameraFeature feature =
                ((FreeCameraBinding) Minecraft.getMinecraft()).pit12$freeCamera();
        boolean active = feature != null && feature.isActive();
        pit12$rebuildCameraChunks = active || pit12$wasFreeCameraActive;
        pit12$wasFreeCameraActive = active;
    }

    // Chunk packets normally mark these for rebuilding when the player moves.
    @Redirect(method = "updateChunkPositions", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/RenderChunk;setPosition(Lnet/minecraft/util/BlockPos;)V"))
    private void pit12$rebuildCameraChunk(RenderChunk chunk, BlockPos position) {
        chunk.setPosition(position);
        if (pit12$rebuildCameraChunks) {
            chunk.setNeedsUpdate(true);
        }
    }
}
