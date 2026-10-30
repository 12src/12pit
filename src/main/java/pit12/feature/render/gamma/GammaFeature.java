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
package pit12.feature.gamma;

import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.shared.lifecycle.ClientLifecycle;

public final class GammaFeature implements ClientLifecycle, ConfigChangeListener {
    private final ConfigCatalog configs;
    private final GammaConfig config;
    private final GammaBinding binding;
    private boolean started;

    public GammaFeature(ConfigCatalog configs, GammaConfig config, GammaBinding binding) {
        this.configs = configs;
        this.config = config;
        this.binding = binding;
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        configs.addListener(this);
        binding.pit12$bindGammaConfig(config);
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        configs.removeListener(this);
        binding.pit12$bindGammaConfig(null);
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (started && (changes.affects("gamma", "enabled") || changes.affects("gamma", "gamma"))) {
            binding.pit12$bindGammaConfig(config);
        }
    }
}
