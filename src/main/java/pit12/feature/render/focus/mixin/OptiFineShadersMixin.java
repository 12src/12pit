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

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "net.optifine.shaders.Shaders", remap = false)
public interface OptiFineShadersMixin {
    @Accessor(value = "isShadowPass", remap = false)
    static boolean pit12$isShadowPass() {
        throw new AssertionError();
    }

    @Invoker(value = "setBlockEntityId", remap = false)
    static void pit12$setBlockEntityId(TileEntity entity) {
        throw new AssertionError();
    }

    @Invoker(value = "nextEntity", remap = false)
    static void pit12$nextEntity(Entity entity) {
        throw new AssertionError();
    }

    @Invoker(value = "setEntityId", remap = false)
    static void pit12$setEntityId(Entity entity) {
        throw new AssertionError();
    }

    @Invoker(value = "endBlockEntities", remap = false)
    static void pit12$endBlockEntities() {
        throw new AssertionError();
    }
}
