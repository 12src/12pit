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
package pit12.feature.misc.autodenick;

import pit12.feature.client.denick.api.DenickAutomation;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.shared.lifecycle.ClientLifecycle;

public final class AutoDenickFeature implements ClientLifecycle, ConfigChangeListener {
    private final ConfigCatalog configs;
    private final AutoDenickConfig config;
    private final DenickAutomation denick;

    public AutoDenickFeature(ConfigCatalog configs, AutoDenickConfig config,
            DenickAutomation denick) {
        this.configs = configs;
        this.config = config;
        this.denick = denick;
        // Set the initial policy before Denick sees the first players.
        updateAutomation();
    }

    @Override
    public void start() {
        configs.addListener(this);
        updateAutomation();
    }

    @Override
    public void stop() {
        configs.removeListener(this);
        denick.setAutomaticQueries(false, false, config.successOnly.get());
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (changes.affects(config.id(), "enabled")
                || changes.affects(config.id(), config.monitor.id())
                || changes.affects(config.id(), config.successOnly.id())) {
            updateAutomation();
        }
    }

    private void updateAutomation() {
        denick.setAutomaticQueries(config.enabled(), config.monitor.get(),
                config.successOnly.get());
    }
}
