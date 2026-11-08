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
package pit12.feature.render.chams;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import pit12.shared.lifecycle.ClientLifecycle;

public final class ChamsFeature implements ClientLifecycle {
    private final RenderManager renderManager;
    private final ChamsConfig config;

    public ChamsFeature(RenderManager renderManager, ChamsConfig config) {
        this.renderManager = renderManager;
        this.config = config;
    }

    @Override
    public void start() {
        for (RenderPlayer renderer : renderManager.getSkinMap().values()) {
            ((ChamsBinding) renderer).pit12$bindChamsConfig(config);
        }
    }

    @Override
    public void stop() {
        for (RenderPlayer renderer : renderManager.getSkinMap().values()) {
            ((ChamsBinding) renderer).pit12$bindChamsConfig(null);
        }
    }
}
