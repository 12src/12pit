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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import pit12.feature.autofish.AutoFishConfig;
import pit12.feature.autofish.AutoFishFeature;
import pit12.feature.discordrpc.DiscordRpcFeature;
import pit12.feature.eventlist.EventListConfig;
import pit12.feature.eventlist.EventListFeature;
import pit12.feature.gamma.GammaBinding;
import pit12.feature.gamma.GammaConfig;
import pit12.feature.gamma.GammaFeature;
import pit12.feature.hudeditor.HudEditorFeature;
import pit12.feature.itemesp.ItemEspConfig;
import pit12.feature.itemesp.ItemEspFeature;
import pit12.feature.playeresp.PlayerEspConfig;
import pit12.feature.playeresp.PlayerEspFeature;
import pit12.feature.playerlist.PlayerListConfig;
import pit12.feature.playerlist.PlayerListFeature;
import pit12.feature.profile.ProfilesFeature;
import pit12.feature.profile.api.Profiles;
import pit12.feature.quickmath.AutoQuickMathConfig;
import pit12.feature.quickmath.AutoQuickMathFeature;
import pit12.feature.relation.RelationFeature;
import pit12.feature.relation.api.RelationLookup;
import pit12.feature.relation.api.Relations;
import pit12.feature.sprint.AutoSprintConfig;
import pit12.feature.sprint.AutoSprintFeature;
import pit12.feature.swap.SwapConfig;
import pit12.feature.swap.SwapFeature;
import pit12.feature.swap.SwapHooksBinding;
import pit12.feature.swap.api.SwapBindings;
import pit12.feature.tooltip.TooltipConfig;
import pit12.feature.tooltip.TooltipFeature;
import pit12.feature.webui.WebUiConfig;
import pit12.feature.webui.WebUiFeature;
import pit12.platform.command.ForgeCommandAdapter;
import pit12.platform.oneconfig.OneConfigSupport;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.languages.Languages;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.PitContextTracker;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentTracker;
import pit12.runtime.player.PlayerNameCache;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceTracker;
import pit12.runtime.session.ClientSession;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.input.MinecraftActions;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.lifecycle.ClientShutdownBinding;

public final class ClientBootstrap {
    private static final Logger LOGGER = Logger.getLogger(ClientBootstrap.class.getName());
    private final List<ClientLifecycle> components = new ArrayList<ClientLifecycle>();
    private final List<ClientLifecycle> startedComponents = new ArrayList<ClientLifecycle>();
    private final ClientThread client;
    private final Languages language;
    private final ClientShutdownBinding shutdown;
    private boolean started;

    public ClientBootstrap() {
        // BootstrapLayoutTest reads the section and category comments.
        // Shared runtime
        Minecraft minecraft = Minecraft.getMinecraft();
        client = new ClientThread(minecraft::isCallingFromMinecraftThread,
                task -> minecraft.addScheduledTask(task));
        language = new Languages(client);
        ConfigCatalog configs = new ConfigCatalog(client);
        ConfigCatalog webUiConfigs = new ConfigCatalog(client);
        shutdown = (ClientShutdownBinding) minecraft;
        ClientSession session = new ClientSession(minecraft, client);
        components.add(session);
        CommandRegistry commands =
                new CommandRegistry(client, ForgeCommandAdapter::register, language);
        components.add(commands);
        PitContextTracker pitContext = new PitContextTracker(session);
        components.add(pitContext);
        PlayerEquipmentTracker playerEquipment = new PlayerEquipmentTracker(session);
        components.add(playerEquipment);
        TabPresenceTracker presence = new TabPresenceTracker(session);
        components.add(presence);
        PlayerNameCache playerNames = new PlayerNameCache(minecraft, session, presence);
        components.add(playerNames);
        HudRegistry hudRegistry = new HudRegistry(client);
        // Feature providers
        // Category: No config
        Profiles profiles =
                registerProfiles(configs, new File(minecraft.mcDataDir, "12pit/config").toPath());
        HudEditorFeature hudEditor = registerHudEditor(hudRegistry, commands, configs, profiles);
        Relations relations = registerRelations(presence,
                new File(minecraft.mcDataDir, "12pit/relations.json").toPath(), client, commands);
        // Category: Player
        SwapBindings swapBindings = registerSwap(minecraft, client, configs, session, pitContext,
                commands, new File(minecraft.mcDataDir, "12pit/swap-bindings.json").toPath(),
                (SwapHooksBinding) minecraft);
        // Category: Interface
        BooleanSetting discordRpc =
                registerWebUi(webUiConfigs, configs, profiles, relations, hudEditor, swapBindings,
                        new File(minecraft.mcDataDir, "12pit/webui.json").toPath());
        // Features
        // Category: No config
        registerDiscordRpc(webUiConfigs, discordRpc);
        // Category: Player
        registerAutoSprint(configs);
        // Category: Utility
        registerAutoFish(configs, session, (MinecraftActions) minecraft);
        registerAutoQuickMath(configs);
        // Category: Render
        registerEventList(configs, hudRegistry, commands);
        registerGamma(configs, (GammaBinding) minecraft.entityRenderer);
        registerItemEsp(configs, session);
        registerPlayerEsp(configs, session, presence, relations);
        registerPlayerList(configs, playerNames, playerEquipment, pitContext, hudRegistry,
                relations, presence);
        registerTooltip(configs);
        // Platform integrations
        OneConfigSupport oneConfig = new OneConfigSupport(configs);
        components.add(oneConfig);
        language.addListener(() -> configs.localize(language));
        language.addListener(() -> webUiConfigs.localize(language));
        webUiConfigs.freeze();
        configs.freeze();
    }

    // Feature providers
    // Category: No config
    private Profiles registerProfiles(ConfigCatalog configs, Path directory) {
        ProfilesFeature profiles = new ProfilesFeature(configs, directory);
        components.add(profiles);
        return profiles;
    }

    private HudEditorFeature registerHudEditor(HudRegistry hudRegistry, CommandRegistry commands,
            ConfigCatalog configs, Profiles profiles) {
        HudEditorFeature hudEditor =
                new HudEditorFeature(hudRegistry, commands, language, configs, profiles);
        components.add(hudEditor);
        return hudEditor;
    }

    private Relations registerRelations(TabPresence presence, Path path, ClientThread client,
            CommandRegistry commands) {
        RelationFeature relations = new RelationFeature(presence, path, client, commands, language);
        components.add(relations);
        return relations;
    }

    // Category: Player
    private SwapBindings registerSwap(Minecraft minecraft, ClientThread client,
            ConfigCatalog configs, ClientSession session, PitContext pitContext,
            CommandRegistry commands, Path path, SwapHooksBinding binding) {
        SwapConfig config = new SwapConfig();
        configs.register(config);
        SwapFeature swap = new SwapFeature(minecraft, client, configs, config, session, pitContext,
                commands, path, binding, language);
        components.add(swap);
        return swap.bindings();
    }

    // Category: Interface
    private BooleanSetting registerWebUi(ConfigCatalog configs, ConfigCatalog featureConfigs,
            Profiles profiles, Relations relations, HudEditorFeature hudEditor,
            SwapBindings swapBindings, Path path) {
        WebUiConfig config = new WebUiConfig(language);
        configs.register(config);
        WebUiFeature webUi = new WebUiFeature(featureConfigs, configs, profiles, relations,
                hudEditor, swapBindings, config, language, path);
        hudEditor.setWebUiOpener(config.keybind()::get, webUi::open, config.hudEditorHint());
        components.add(webUi);
        return config.discordRpc();
    }

    // Features
    // Category: No config
    private void registerDiscordRpc(ConfigCatalog configs, BooleanSetting enabled) {
        components.add(new DiscordRpcFeature(configs, enabled));
    }

    // Category: Player
    private void registerAutoSprint(ConfigCatalog configs) {
        AutoSprintConfig config = new AutoSprintConfig();
        configs.register(config);
        components.add(new AutoSprintFeature(config));
    }

    // Category: Utility
    private void registerAutoFish(ConfigCatalog configs, ClientSession session,
            MinecraftActions actions) {
        AutoFishConfig config = new AutoFishConfig();
        configs.register(config);
        components.add(new AutoFishFeature(configs, config, session, actions));
    }

    private void registerAutoQuickMath(ConfigCatalog configs) {
        AutoQuickMathConfig config = new AutoQuickMathConfig();
        configs.register(config);
        components.add(new AutoQuickMathFeature(config));
    }

    // Category: Render
    private void registerEventList(ConfigCatalog configs, HudRegistry hudRegistry,
            CommandRegistry commands) {
        EventListConfig config = new EventListConfig();
        configs.register(config);
        components.add(new EventListFeature(configs, config, hudRegistry, commands, language));
    }

    private void registerGamma(ConfigCatalog configs, GammaBinding binding) {
        GammaConfig config = new GammaConfig();
        configs.register(config);
        components.add(new GammaFeature(configs, config, binding));
    }

    private void registerItemEsp(ConfigCatalog configs, ClientSession session) {
        ItemEspConfig config = new ItemEspConfig();
        configs.register(config);
        components.add(new ItemEspFeature(configs, config, session));
    }

    private void registerPlayerEsp(ConfigCatalog configs, ClientSession session,
            TabPresence presence, RelationLookup relations) {
        PlayerEspConfig config = new PlayerEspConfig();
        configs.register(config);
        components.add(new PlayerEspFeature(configs, config, session, presence, relations));
    }

    private void registerPlayerList(ConfigCatalog configs, PlayerNameCache playerNames,
            PlayerEquipmentAccess playerEquipment, PitContext pitContext, HudRegistry hudRegistry,
            RelationLookup relations, TabPresence presence) {
        PlayerListConfig config = new PlayerListConfig();
        configs.register(config);
        components.add(new PlayerListFeature(configs, config, playerNames, playerEquipment,
                pitContext, hudRegistry, relations, presence, language));
    }

    private void registerTooltip(ConfigCatalog configs) {
        TooltipConfig config = new TooltipConfig();
        configs.register(config);
        components.add(new TooltipFeature(configs, config));
    }

    public void start() {
        client.check();
        if (started) {
            return;
        }
        try {
            for (ClientLifecycle component : components) {
                // A failed start may leave resources that stop() must release.
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
