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
package pit12.platform.mixin.feature.gamma;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.gamma.GammaBinding;
import pit12.feature.gamma.GammaConfig;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin implements GammaBinding {
    @Shadow
    private boolean lightmapUpdateNeeded;
    @Unique
    private GammaConfig pit12$gammaConfig;

    @Override
    public void pit12$bindGammaConfig(GammaConfig config) {
        pit12$gammaConfig = config;
        lightmapUpdateNeeded = true;
    }

    // Override the render value so Minecraft still saves the user's own brightness.
    @Redirect(method = "updateLightmap",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/settings/GameSettings;gammaSetting:F"),
            require = 0)
    private float pit12$useConfiguredGamma(GameSettings settings) {
        GammaConfig config = pit12$gammaConfig;
        return config != null && config.enabled() ? config.gamma() : settings.gammaSetting;
    }
}
