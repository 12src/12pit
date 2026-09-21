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
package pit12.bootstrap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import pit12.feature.Feature;
import pit12.feature.clickgui.ClickGuiConfig;
import pit12.feature.clickgui.ClickGuiFeature;
import pit12.feature.hudeditor.HudEditorFeature;
import pit12.feature.profile.ProfilesFeature;
import pit12.feature.tooltip.TooltipConfig;
import pit12.feature.tooltip.TooltipFeature;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.hud.HudRegistry;
import pit12.shared.build.BuildInfo;

public final class ClientBootstrap {
    private static final Logger LOGGER = Logger.getLogger(ClientBootstrap.class.getName());
    private final List<Feature> features = new ArrayList<Feature>();
    private final List<Feature> startedFeatures = new ArrayList<Feature>();
    private final ConfigCatalog configs;
    private boolean started;

    public ClientBootstrap() {
        configs = new ConfigCatalog();
        ClickGuiConfig clickGuiConfig = new ClickGuiConfig();
        TooltipConfig tooltipConfig = new TooltipConfig();
        configs.register(clickGuiConfig);
        configs.register(tooltipConfig);
        configs.freeze();
        File profileDirectory = new File(Minecraft.getMinecraft().mcDataDir, "12pit/config");
        ProfilesFeature profiles = new ProfilesFeature(configs, profileDirectory.toPath());
        HudRegistry hudRegistry = new HudRegistry();
        HudEditorFeature hudEditor = new HudEditorFeature(hudRegistry);
        BuildInfo buildInfo = new BuildInfo(BuildConfig.MOD_NAME, BuildConfig.VERSION,
                BuildConfig.GIT_COMMIT, BuildConfig.RELEASE_BUILD);
        features.add(profiles);
        features.add(new TooltipFeature(tooltipConfig));
        features.add(hudEditor);
        features.add(new ClickGuiFeature(configs, profiles, clickGuiConfig, hudEditor, buildInfo));
    }

    /**
     * Starts all configured features in order. Calling it again after a successful start has no effect. If a feature throws
     * a {@link RuntimeException}, every feature attempted so far is stopped before the exception is rethrown.
     */
    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            for (Feature feature : features) {
                // A failed start may still acquire resources that rollback must release.
                startedFeatures.add(feature);
                feature.start();
            }
            started = true;
        } catch (RuntimeException failure) {
            stopStartedFeatures();
            throw failure;
        }
    }

    /**
     * Stops features in reverse order so each one remains available until anything using it has stopped. Cleanup continues
     * if a feature throws a {@link RuntimeException}; the exception is logged instead of being passed to the caller.
     */
    public synchronized void stop() {
        stopStartedFeatures();
        started = false;
    }

    private void stopStartedFeatures() {
        // Dependencies must remain available until their consumers have stopped.
        for (int index = startedFeatures.size() - 1; index >= 0; index--) {
            Feature feature = startedFeatures.get(index);
            try {
                feature.stop();
            } catch (RuntimeException failure) {
                // One failed cleanup must not prevent the remaining features from releasing resources.
                LOGGER.log(Level.WARNING, "Failed to stop feature " + feature.getClass().getName(),
                        failure);
            }
        }
        startedFeatures.clear();
    }
}
