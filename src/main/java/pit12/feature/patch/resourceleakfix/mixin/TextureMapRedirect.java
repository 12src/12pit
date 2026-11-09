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
package pit12.feature.patch.resourceleakfix.mixin;

import java.io.InputStream;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.patch.resourceleakfix.ResourceLeakFixBinding;
import pit12.feature.patch.resourceleakfix.ResourceLeakFixConfig;

@Mixin(value = TextureMap.class, priority = -1)
public abstract class TextureMapRedirect {
    @Dynamic("Added by OptiFine")
    @Redirect(method = "detectMinimumSpriteSize", remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/IResource;getInputStream()Ljava/io/InputStream;",
            remap = true))
    private InputStream pit12$closeUnusedMetadata(IResource resource) {
        ResourceLeakFixConfig config =
                ((ResourceLeakFixBinding) this).pit12$resourceLeakFixConfig();
        // The first texture reload runs before the config is bound.
        if ((config == null || config.enabled()) && resource instanceof SimpleResourceAccessor) {
            IOUtils.closeQuietly(((SimpleResourceAccessor) resource).pit12$metadataInputStream());
        }
        return resource.getInputStream();
    }
}
