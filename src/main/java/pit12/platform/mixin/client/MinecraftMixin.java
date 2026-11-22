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
package pit12.platform.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.runtime.item.InventoryClicks;
import pit12.runtime.session.ClientSessionBinding;
import pit12.shared.lifecycle.ClientShutdownBinding;
import pit12.shared.rendering.PlayerOpacity;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements ClientShutdownBinding, ClientSessionBinding,
        PlayerOpacity.Binding, InventoryClicks.Binding {
    @Unique
    private Runnable pit12$shutdown;
    @Unique
    private Runnable pit12$worldChangeListener;
    @Unique
    private PlayerOpacity pit12$playerOpacity;
    @Unique
    private InventoryClicks pit12$inventoryClicks;

    @Override
    public void pit12$bindInventoryClicks(InventoryClicks clicks) {
        pit12$inventoryClicks = clicks;
    }

    @Override
    public InventoryClicks pit12$inventoryClicks() {
        return pit12$inventoryClicks;
    }

    @Override
    public void pit12$bindPlayerOpacity(PlayerOpacity opacity) {
        pit12$playerOpacity = opacity;
    }

    @Override
    public float pit12$playerOpacity(EntityPlayer player) {
        return pit12$playerOpacity == null ? 1.0F : pit12$playerOpacity.pit12$playerOpacity(player);
    }

    @Override
    public void bindClientShutdown(Runnable shutdown) {
        pit12$shutdown = shutdown;
    }

    @Override
    public void bindWorldChangeListener(Runnable listener) {
        pit12$worldChangeListener = listener;
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
            at = @At("RETURN"))
    private void pit12$afterWorldChange(WorldClient world, String message, CallbackInfo ci) {
        if (pit12$worldChangeListener != null)
            pit12$worldChangeListener.run();
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void pit12$beforeShutdown(CallbackInfo ci) {
        Runnable shutdown = pit12$shutdown;
        pit12$shutdown = null;
        if (shutdown != null)
            shutdown.run();
    }
}
