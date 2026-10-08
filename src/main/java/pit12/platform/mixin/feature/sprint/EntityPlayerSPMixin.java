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
package pit12.platform.mixin.feature.sprint;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.sprint.AutoSprintBinding;
import pit12.feature.sprint.AutoSprintConfig;

@Mixin(EntityPlayerSP.class)
public abstract class EntityPlayerSPMixin extends AbstractClientPlayer
        implements AutoSprintBinding {
    @Unique
    private AutoSprintConfig pit12$sprintConfig;

    protected EntityPlayerSPMixin(World world, GameProfile gameProfile) {
        super(world, gameProfile);
    }

    @Override
    public void pit12$bindSprintConfig(AutoSprintConfig config) {
        pit12$sprintConfig = config;
    }

    // Both sprint-key checks use this override, so vanilla still decides when sprinting is allowed.
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z"))
    private boolean pit12$sprintAutomatically(KeyBinding key) {
        return key.isKeyDown() || (pit12$sprintConfig != null && pit12$sprintConfig.enabled());
    }
}
