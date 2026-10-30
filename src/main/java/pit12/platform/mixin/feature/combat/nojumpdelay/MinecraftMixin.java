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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pit12.feature.combat.nojumpdelay.NoJumpDelayBinding;
import pit12.feature.combat.nojumpdelay.NoJumpDelayConfig;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements NoJumpDelayBinding {
    @Unique
    private NoJumpDelayConfig pit12$noJumpDelayConfig;

    @Override
    public void pit12$bindNoJumpDelayConfig(NoJumpDelayConfig config) {
        pit12$noJumpDelayConfig = config;
    }

    @Override
    public boolean pit12$noJumpDelayEnabled() {
        return pit12$noJumpDelayConfig != null && pit12$noJumpDelayConfig.enabled();
    }
}
