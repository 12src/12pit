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
import pit12.feature.gamma.GammaBinding;
import pit12.feature.gamma.GammaConfig;
import pit12.feature.gamma.GammaFeature;
import pit12.feature.hudeditor.HudEditorFeature;
import pit12.feature.playerlist.PlayerListConfig;
import pit12.feature.playerlist.PlayerListFeature;
import pit12.feature.profile.ProfilesFeature;
import pit12.feature.quickmath.AutoQuickMathConfig;
import pit12.feature.quickmath.AutoQuickMathFeature;
import pit12.feature.relation.RelationFeature;
import pit12.feature.sprint.AutoSprintConfig;
import pit12.feature.sprint.AutoSprintFeature;
import pit12.feature.tooltip.TooltipConfig;
import pit12.feature.tooltip.TooltipFeature;
import pit12.feature.webui.WebUiConfig;
import pit12.feature.webui.WebUiFeature;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.pit.PitContextTracker;
import pit12.runtime.player.PlayerEquipmentTracker;
import pit12.runtime.player.TabPresenceTracker;
import pit12.runtime.session.ClientSession;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.lifecycle.ClientShutdownBinding;

public final class ClientBootstrap {
    private static final Logger LOGGER = Logger.getLogger(ClientBootstrap.class.getName());
    private final List<ClientLifecycle> components = new ArrayList<ClientLifecycle>();
    private final List<ClientLifecycle> startedComponents = new ArrayList<ClientLifecycle>();
    private final ConfigCatalog configs;
    private final ClientThread client;
    private final ClientShutdownBinding shutdown;
    private boolean started;

    public ClientBootstrap() {
        Minecraft minecraft = Minecraft.getMinecraft();
        client = new ClientThread(minecraft::isCallingFromMinecraftThread,
                task -> minecraft.addScheduledTask(task));
        configs = new ConfigCatalog(client);
        shutdown = (ClientShutdownBinding) minecraft;
        ClientSession session = new ClientSession(minecraft, client);
        CommandRegistry commands = new CommandRegistry(client);
        WebUiConfig webUiConfig = new WebUiConfig();
        PlayerListConfig playerListConfig = new PlayerListConfig();
        TooltipConfig tooltipConfig = new TooltipConfig();
        GammaConfig gammaConfig = new GammaConfig();
        AutoSprintConfig autoSprintConfig = new AutoSprintConfig();
        AutoQuickMathConfig autoQuickMathConfig = new AutoQuickMathConfig();
        configs.register(webUiConfig);
        configs.register(playerListConfig);
        configs.register(tooltipConfig);
        configs.register(gammaConfig);
        configs.register(autoSprintConfig);
        configs.register(autoQuickMathConfig);
        configs.freeze();
        File profileDirectory = new File(Minecraft.getMinecraft().mcDataDir, "12pit/config");
        ProfilesFeature profiles = new ProfilesFeature(configs, profileDirectory.toPath());
        PlayerEquipmentTracker playerEquipment = new PlayerEquipmentTracker(session);
        TabPresenceTracker presence = new TabPresenceTracker(session);
        RelationFeature relations = new RelationFeature(presence,
                new File(Minecraft.getMinecraft().mcDataDir, "12pit/relations.json").toPath(),
                client, commands);
        PitContextTracker pitContext = new PitContextTracker(session);
        HudRegistry hudRegistry = new HudRegistry(client);
        HudEditorFeature hudEditor = new HudEditorFeature(hudRegistry, commands);
        PlayerListFeature playerList = new PlayerListFeature(configs, playerListConfig,
                playerEquipment, pitContext, hudRegistry, relations, presence);
        components.add(session);
        components.add(commands);
        components.add(profiles);
        components.add(playerEquipment);
        components.add(presence);
        components.add(relations);
        components.add(pitContext);
        components.add(playerList);
        components.add(new TooltipFeature(configs, tooltipConfig));
        components.add(
                new GammaFeature(configs, gammaConfig, (GammaBinding) minecraft.entityRenderer));
        components.add(new AutoSprintFeature(autoSprintConfig));
        components.add(new AutoQuickMathFeature(autoQuickMathConfig));
        components.add(hudEditor);
        components.add(new WebUiFeature(configs, profiles, relations, hudEditor, webUiConfig));
    }

    public void start() {
        client.check();
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
            shutdown.bindClientShutdown(this::stop);
        } catch (RuntimeException failure) {
            stopStartedComponents();
            throw failure;
        }
    }

    public void stop() {
        client.check();
        shutdown.bindClientShutdown(null);
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
