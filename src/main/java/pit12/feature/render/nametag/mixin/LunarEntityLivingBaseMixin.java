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
package pit12.feature.render.nametag.mixin;

import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.feature.render.nametag.NametagNamesBinding;
import pit12.platform.lunar.LunarNametagText;

// Run after Lunar adds the component getter at priority 200.
@Mixin(value = EntityLivingBase.class, priority = 100)
public abstract class LunarEntityLivingBaseMixin {
    @Unique
    private Object pit12$originalName;
    @Unique
    private String pit12$nameSuffix;
    @Unique
    private Object pit12$cachedName;

    @Dynamic("Lunar's display name component getter")
    @Inject(method = "bridge$getDisplayNameComponent", at = @At("RETURN"), cancellable = true,
            remap = false, require = 1)
    private void pit12$displayName(CallbackInfoReturnable<Object> callback) {
        Function<UUID, String> names =
                ((NametagNamesBinding) Minecraft.getMinecraft()).pit12$nametagNames();
        if (names == null || !((Object) this instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) (Object) this;
        String suffix = names.apply(player.getUniqueID());
        if (suffix.isEmpty()) {
            pit12$originalName = null;
            pit12$nameSuffix = null;
            pit12$cachedName = null;
            return;
        }
        Object original = callback.getReturnValue();
        if (original != pit12$originalName || !suffix.equals(pit12$nameSuffix)) {
            pit12$cachedName = LunarNametagText.append(original, suffix);
            pit12$originalName = original;
            pit12$nameSuffix = suffix;
        }
        callback.setReturnValue(pit12$cachedName);
    }
}
