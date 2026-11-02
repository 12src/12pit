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
package pit12.platform.mixin.player;

import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pit12.runtime.player.PlayerHealth;
import pit12.runtime.player.PlayerHealthBinding;

@Mixin(EntityPlayer.class)
public abstract class EntityPlayerMixin implements PlayerHealthBinding {
    @Unique
    private PlayerHealth pit12$health;

    @Override
    public PlayerHealth pit12$health() {
        if (pit12$health == null) {
            pit12$health = new PlayerHealth(((EntityPlayer) (Object) this).getHealth());
        }
        return pit12$health;
    }
}
