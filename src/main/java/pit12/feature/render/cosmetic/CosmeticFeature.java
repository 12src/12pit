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
package pit12.feature.render.cosmetic;

import net.minecraft.client.Minecraft;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.shared.lifecycle.ClientLifecycle;

public final class CosmeticFeature implements ClientLifecycle, ConfigChangeListener {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final CosmeticConfig config;
    private final CosmeticBinding binding;
    private final CosmeticModelLoader loader;
    private CosmeticModel model;
    private boolean started;

    public CosmeticFeature(Minecraft minecraft, ConfigCatalog configs, CosmeticConfig config,
            CosmeticBinding binding) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.binding = binding;
        loader = new CosmeticModelLoader();
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        configs.addListener(this);
        apply();
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        configs.removeListener(this);
        binding.pit12$bindCosmeticRenderer(null);
        disposeModel();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (started && changes.affects("cat_ears", "style")) {
            apply();
        }
    }

    private void apply() {
        disposeModel();
        model = loader.load(config.style());
        binding.pit12$bindCosmeticRenderer(
                model == null ? null : new CosmeticRenderer(minecraft, config, model));
    }

    private void disposeModel() {
        if (model != null) {
            model.dispose();
            model = null;
        }
    }
}
