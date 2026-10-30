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
package pit12.platform.mixin.feature.combat.nojumpdelay;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.combat.nojumpdelay.NoJumpDelayBinding;

@Mixin(EntityLivingBase.class)
public abstract class EntityLivingBaseMixin {
    @Shadow
    private int jumpTicks;

    // The third read checks the cooldown before a ground jump.
    @Redirect(method = "onLivingUpdate",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;jumpTicks:I",
                    opcode = Opcodes.GETFIELD, ordinal = 2),
            require = 1)
    private int pit12$jumpCooldown(EntityLivingBase entity) {
        Minecraft minecraft = Minecraft.getMinecraft();
        return entity == minecraft.thePlayer
                && ((NoJumpDelayBinding) minecraft).pit12$noJumpDelayEnabled() ? 0 : jumpTicks;
    }
}
