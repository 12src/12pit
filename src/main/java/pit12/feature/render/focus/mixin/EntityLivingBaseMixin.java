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

import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.shared.entity.HurtTimeAccess;

@Mixin(EntityLivingBase.class)
public abstract class EntityLivingBaseMixin implements HurtTimeAccess {
    @Shadow
    public int hurtTime;
    @Unique
    private int pit12$realHurtTime = -1;
    @Unique
    private int pit12$realResistantTime;

    @Override
    public int pit12$realHurtTime() {
        return pit12$realHurtTime < 0 ? hurtTime : pit12$realHurtTime;
    }

    @Override
    public void pit12$overrideHurtTime(int time, int resistantTime) {
        EntityHurtTimeAccessor entity = (EntityHurtTimeAccessor) this;
        if (pit12$realHurtTime < 0) {
            pit12$realHurtTime = hurtTime;
            pit12$realResistantTime = entity.pit12$getHurtResistantTime();
        }
        hurtTime = time;
        entity.pit12$setHurtResistantTime(resistantTime);
    }

    // Vanilla must tick and replace the real timers, not the targeting values.
    @Inject(method = {"onEntityUpdate", "handleStatusUpdate", "performHurtAnimation"},
            at = @At("HEAD"))
    private void pit12$restoreHurtTime(CallbackInfo ci) {
        if (pit12$realHurtTime >= 0) {
            hurtTime = pit12$realHurtTime;
            ((EntityHurtTimeAccessor) this).pit12$setHurtResistantTime(pit12$realResistantTime);
            pit12$realHurtTime = -1;
        }
    }
}
