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

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pit12.feature.render.focus.FocusBinding;
import pit12.feature.render.focus.FocusFeature;
import pit12.feature.render.focus.api.FocusOpacity;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements FocusBinding, FocusOpacity {
    @Unique
    private FocusFeature pit12$focus;

    @Override
    public void pit12$bindFocus(FocusFeature feature) {
        pit12$focus = feature;
    }

    @Override
    public float pit12$focusOpacity(EntityPlayer player) {
        return pit12$focus == null ? 1.0F : pit12$focus.opacity(player);
    }
}
