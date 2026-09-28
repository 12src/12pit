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
import pit12.feature.hudeditor.HudEditorFeature;
import pit12.feature.playerlist.PlayerListConfig;
import pit12.feature.playerlist.PlayerListFeature;
import pit12.feature.profile.ProfilesFeature;
import pit12.feature.relation.RelationFeature;
import pit12.feature.sprint.AutoSprintConfig;
import pit12.feature.sprint.AutoSprintFeature;
import pit12.feature.tooltip.TooltipConfig;
import pit12.feature.tooltip.TooltipFeature;
import pit12.feature.webui.WebUiConfig;
import pit12.feature.webui.WebUiFeature;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.pit.PitContextTracker;
import pit12.runtime.player.PlayerEquipmentTracker;
import pit12.runtime.player.TabPresenceTracker;
import pit12.shared.lifecycle.ClientLifecycle;

public final class ClientBootstrap {
    private static final Logger LOGGER = Logger.getLogger(ClientBootstrap.class.getName());
    private final List<ClientLifecycle> components = new ArrayList<ClientLifecycle>();
    private final List<ClientLifecycle> startedComponents = new ArrayList<ClientLifecycle>();
    private final ConfigCatalog configs;
    private boolean started;

    public ClientBootstrap() {
        configs = new ConfigCatalog();
        WebUiConfig webUiConfig = new WebUiConfig();
        PlayerListConfig playerListConfig = new PlayerListConfig();
        TooltipConfig tooltipConfig = new TooltipConfig();
        AutoSprintConfig autoSprintConfig = new AutoSprintConfig();
        configs.register(webUiConfig);
        configs.register(playerListConfig);
        configs.register(tooltipConfig);
        configs.register(autoSprintConfig);
        configs.freeze();
        File profileDirectory = new File(Minecraft.getMinecraft().mcDataDir, "12pit/config");
        ProfilesFeature profiles = new ProfilesFeature(configs, profileDirectory.toPath());
        PlayerEquipmentTracker playerEquipment = new PlayerEquipmentTracker();
        TabPresenceTracker presence = new TabPresenceTracker();
        RelationFeature relations = new RelationFeature(presence,
                new File(Minecraft.getMinecraft().mcDataDir, "12pit/relations.json").toPath());
        PitContextTracker pitContext = new PitContextTracker();
        HudRegistry hudRegistry = new HudRegistry();
        HudEditorFeature hudEditor = new HudEditorFeature(hudRegistry);
        PlayerListFeature playerList = new PlayerListFeature(configs, playerListConfig,
                playerEquipment, pitContext, hudRegistry, relations, presence);
        components.add(profiles);
        components.add(playerEquipment);
        components.add(presence);
        components.add(relations);
        components.add(pitContext);
        components.add(playerList);
        components.add(new TooltipFeature(configs, tooltipConfig));
        components.add(new AutoSprintFeature(autoSprintConfig));
        components.add(hudEditor);
        components.add(new WebUiFeature(configs, profiles, relations, hudEditor, webUiConfig));
    }

    // TODO: Revisit lifecycle management if feature enable rules repeat or shared trackers run without consumers.
    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            for (ClientLifecycle component : components) {
                // A failed start may still acquire resources that rollback must release.
                startedComponents.add(component);
                component.start();
            }
            started = true;
        } catch (RuntimeException failure) {
            stopStartedComponents();
            throw failure;
        }
    }

    public synchronized void stop() {
        stopStartedComponents();
        started = false;
    }

    private void stopStartedComponents() {
        // Dependencies must remain available until their consumers have stopped.
        for (int index = startedComponents.size() - 1; index >= 0; index--) {
            ClientLifecycle component = startedComponents.get(index);
            try {
                component.stop();
            } catch (RuntimeException failure) {
                LOGGER.log(Level.WARNING,
                        "Failed to stop component " + component.getClass().getName(), failure);
            }
        }
        startedComponents.clear();
    }
}
