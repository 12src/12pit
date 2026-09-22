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

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.runtime.player.PlayerEquipmentWorldBinding;
import pit12.runtime.player.PlayerEquipmentWorldObserver;

@Mixin(WorldClient.class)
public abstract class WorldClientEquipmentMixin implements PlayerEquipmentWorldBinding {
    @Unique
    private PlayerEquipmentWorldObserver pit12$playerEquipmentObserver;

    @Override
    public void bindPlayerEquipmentObserver(PlayerEquipmentWorldObserver observer) {
        pit12$playerEquipmentObserver = observer;
    }

    @Inject(method = "removeEntityFromWorld", at = @At("RETURN"))
    private void pit12$afterEntityRemoved(int entityId, CallbackInfoReturnable<Entity> cir) {
        PlayerEquipmentWorldObserver observer = pit12$playerEquipmentObserver;
        Entity removed = cir.getReturnValue();
        if (observer != null && removed != null) {
            observer.onEntityRemoved(removed);
        }
    }
}
