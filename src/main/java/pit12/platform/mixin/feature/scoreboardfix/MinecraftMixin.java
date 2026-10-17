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
package pit12.platform.mixin.feature.scoreboardfix;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pit12.feature.scoreboardfix.ScoreboardFixBinding;
import pit12.feature.scoreboardfix.ScoreboardFixConfig;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements ScoreboardFixBinding {
    @Unique
    private ScoreboardFixConfig pit12$scoreboardFixConfig;

    @Override
    public void pit12$bindScoreboardFixConfig(ScoreboardFixConfig config) {
        pit12$scoreboardFixConfig = config;
    }

    @Override
    public boolean pit12$scoreboardFixEnabled() {
        return pit12$scoreboardFixConfig != null && pit12$scoreboardFixConfig.enabled();
    }
}
