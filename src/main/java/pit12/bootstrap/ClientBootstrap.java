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
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.FMLClientHandler;
import pit12.feature.autoclick.AutoClickBinding;
import pit12.feature.autoclick.AutoClickConfig;
import pit12.feature.autoclick.AutoClickFeature;
import pit12.feature.autofish.AutoFishConfig;
import pit12.feature.autofish.AutoFishFeature;
import pit12.feature.banalert.BanAlertConfig;
import pit12.feature.banalert.BanAlertFeature;
import pit12.feature.chatmacro.ChatMacroConfig;
import pit12.feature.chatmacro.ChatMacroFeature;
import pit12.feature.contractreroll.ContractRerollConfig;
import pit12.feature.contractreroll.ContractRerollFeature;
import pit12.feature.denick.DenickConfig;
import pit12.feature.denick.DenickFeature;
import pit12.feature.denick.DenickNamesBinding;
import pit12.feature.denick.api.DenickNames;
import pit12.feature.discordrpc.DiscordRpcFeature;
import pit12.feature.eventlist.EventListConfig;
import pit12.feature.eventlist.EventListFeature;
import pit12.feature.gamma.GammaBinding;
import pit12.feature.gamma.GammaConfig;
import pit12.feature.gamma.GammaFeature;
import pit12.feature.hudeditor.HudEditorFeature;
import pit12.feature.itemesp.ItemEspConfig;
import pit12.feature.itemesp.ItemEspFeature;
import pit12.feature.nametag.NametagConfig;
import pit12.feature.nametag.NametagFeature;
import pit12.feature.nametag.NametagNamesBinding;
import pit12.feature.nojumpdelay.NoJumpDelayBinding;
import pit12.feature.nojumpdelay.NoJumpDelayConfig;
import pit12.feature.nojumpdelay.NoJumpDelayFeature;
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
import pit12.feature.resourceleakfix.ResourceLeakFixBinding;
import pit12.feature.resourceleakfix.ResourceLeakFixConfig;
import pit12.feature.resourceleakfix.ResourceLeakFixFeature;
import pit12.feature.ringhelper.RingHelperConfig;
import pit12.feature.ringhelper.RingHelperFeature;
import pit12.feature.scoreboardfix.ScoreboardFixBinding;
import pit12.feature.scoreboardfix.ScoreboardFixConfig;
import pit12.feature.scoreboardfix.ScoreboardFixFeature;
import pit12.feature.smartblock.SmartBlockBinding;
import pit12.feature.smartblock.SmartBlockConfig;
import pit12.feature.smartblock.SmartBlockFeature;
import pit12.feature.sprint.AutoSprintBinding;
import pit12.feature.sprint.AutoSprintConfig;
import pit12.feature.sprint.AutoSprintFeature;
import pit12.feature.swap.SwapConfig;
import pit12.feature.swap.SwapFeature;
import pit12.feature.swap.SwapHooksBinding;
import pit12.feature.swap.api.SwapBindings;
import pit12.feature.telebow.TelebowConfig;
import pit12.feature.telebow.TelebowFeature;
import pit12.feature.telebow.api.TelebowCooldowns;
import pit12.feature.timers.TimersConfig;
import pit12.feature.timers.TimersFeature;
import pit12.feature.timers.api.PlayerTimers;
import pit12.feature.tooltip.TooltipConfig;
import pit12.feature.tooltip.TooltipFeature;
import pit12.feature.webui.WebUiConfig;
import pit12.feature.webui.WebUiFeature;
import pit12.platform.discord.DiscordIpcConnection;
import pit12.platform.forge.ForgeCommandAdapter;
import pit12.platform.lunar.LunarSupport;
import pit12.platform.lunar.LunarTextureState;
import pit12.platform.lwjgl.LwjglInputDevice;
import pit12.platform.oneconfig.OneConfigSupport;
import pit12.runtime.arrow.ArrowTracker;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.input.InputBlockBinding;
import pit12.runtime.input.InputBlocker;
import pit12.runtime.languages.Languages;
import pit12.runtime.nick.NickTracker;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.PitContextTracker;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentTracker;
import pit12.runtime.player.PlayerNameCache;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceTracker;
import pit12.runtime.scoreboard.SidebarCache;
import pit12.runtime.session.ClientSession;
import pit12.runtime.session.ClientSessionBinding;
import pit12.runtime.usage.DailyUsageReporter;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.input.MinecraftActions;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.lifecycle.ClientShutdownBinding;
import pit12.shared.rendering.OpenGlTextureState;
import pit12.shared.rendering.TextureState;

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
        ClientSession session =
                new ClientSession(minecraft, client, (ClientSessionBinding) minecraft);
        components.add(session);
        ArrowTracker arrows = new ArrowTracker(minecraft, session);
        components.add(arrows);
        InputBlocker input = new InputBlocker(minecraft, client, (InputBlockBinding) minecraft,
                new LwjglInputDevice());
        components.add(input);
        CommandRegistry commands =
                new CommandRegistry(client, configs, ForgeCommandAdapter::register, language);
        components.add(commands);
        PitContextTracker pitContext = new PitContextTracker(session);
        components.add(pitContext);
        PlayerEntityTracker players = new PlayerEntityTracker(session);
        components.add(players);
        PlayerEquipmentTracker playerEquipment = new PlayerEquipmentTracker(session, players);
        components.add(playerEquipment);
        TabPresenceTracker presence = new TabPresenceTracker(session);
        components.add(presence);
        PlayerNameCache playerNames = new PlayerNameCache(minecraft, session, presence);
        components.add(playerNames);
        NickTracker nicks = new NickTracker(client, presence, playerNames);
        components.add(nicks);
        SidebarCache sidebar = new SidebarCache(minecraft, session);
        components.add(sidebar);
        components.add(new DailyUsageReporter(client, pitContext));
        HudRegistry hudRegistry = new HudRegistry(client);
        Supplier<TextureState> textureStates =
                LunarSupport.isLoaded() ? LunarTextureState::new : OpenGlTextureState::new;
        // Feature providers
        // Category: No config
        Profiles profiles =
                registerProfiles(configs, new File(minecraft.mcDataDir, "12pit/config").toPath());
        HudEditorFeature hudEditor = registerHudEditor(hudRegistry, commands, configs, profiles);
        Relations relations = registerRelations(presence,
                new File(minecraft.mcDataDir, "12pit/relations.json").toPath(), client, commands);
        // Category: Combat
        SwapBindings swapBindings = registerSwap(minecraft, client, configs, session, pitContext,
                playerEquipment, relations, commands,
                new File(minecraft.mcDataDir, "12pit/swap-bindings.json").toPath(),
                (SwapHooksBinding) minecraft, input);
        // Category: No config
        BooleanSetting discordRpc =
                registerWebUi(webUiConfigs, configs, profiles, relations, hudEditor, swapBindings,
                        new File(minecraft.mcDataDir, "12pit/webui.json").toPath());
        // Category: Render
        TelebowCooldowns telebowCooldowns = registerTelebow(minecraft, configs, session, arrows,
                relations, textureStates.get());
        PlayerTimers timers = registerTimers(minecraft, configs, session, arrows, relations,
                playerNames, hudRegistry, telebowCooldowns, players, playerEquipment);
        // Category: Misc
        DenickNames denickNames = registerDenick(minecraft, configs, session, pitContext, presence,
                playerNames, nicks, players, playerEquipment, commands,
                new File(minecraft.mcDataDir, "12pit/denick-cache.json").toPath());
        // Features
        // Category: No config
        registerDiscordRpc(webUiConfigs, discordRpc);
        // Category: Combat
        registerAutoClick(minecraft, configs, session, (AutoClickBinding) minecraft,
                (MinecraftActions) minecraft);
        registerSmartBlock(minecraft, configs, session, (SmartBlockBinding) minecraft);
        // Category: Player
        registerAutoFish(configs, session, (MinecraftActions) minecraft);
        registerAutoSprint(configs, (AutoSprintBinding) minecraft);
        registerChatMacro(minecraft, configs, session, sidebar, input, commands,
                new File(minecraft.mcDataDir, "12pit/chat-macros.json").toPath());
        registerContractReroll(minecraft, configs, session, pitContext, sidebar, input,
                (MinecraftActions) minecraft);
        registerNoJumpDelay(configs, (NoJumpDelayBinding) minecraft);
        // Category: Render
        registerEventList(configs, hudRegistry, commands);
        registerGamma(configs, (GammaBinding) minecraft.entityRenderer);
        registerItemEsp(configs, session, textureStates.get());
        registerNametag(minecraft, configs, session, playerEquipment, denickNames, nicks, timers);
        registerPlayerEsp(configs, session, presence, relations, textureStates.get());
        registerPlayerList(configs, playerNames, players, playerEquipment, pitContext, hudRegistry,
                relations, presence, denickNames, nicks);
        registerRingHelper(minecraft, configs, session, pitContext, textureStates.get());
        registerTooltip(configs);
        // Category: Misc
        registerAutoQuickMath(configs);
        registerBanAlert(minecraft, configs, session, presence);
        // Category: Patch
        if (FMLClientHandler.instance().hasOptifine()) {
            registerResourceLeakFix(configs,
                    (ResourceLeakFixBinding) minecraft.getTextureMapBlocks());
        }
        registerScoreboardFix(configs, (ScoreboardFixBinding) minecraft);
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

    // Category: Combat
    private SwapBindings registerSwap(Minecraft minecraft, ClientThread client,
            ConfigCatalog configs, ClientSession session, PitContext pitContext,
            PlayerEquipmentAccess playerEquipment, RelationLookup relations,
            CommandRegistry commands, Path path, SwapHooksBinding binding, InputBlocker input) {
        SwapConfig config = new SwapConfig();
        configs.register(config);
        SwapFeature swap = new SwapFeature(minecraft, client, configs, config, session, pitContext,
                playerEquipment, relations, commands, path, binding, input, language);
        components.add(swap);
        return swap.bindings();
    }

    // Category: No config
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

    // Category: Render
    private TelebowCooldowns registerTelebow(Minecraft minecraft, ConfigCatalog configs,
            ClientSession session, ArrowTracker arrows, RelationLookup relations,
            TextureState textures) {
        TelebowConfig config = new TelebowConfig();
        configs.register(config);
        TelebowFeature feature = new TelebowFeature(minecraft, configs, config, session, arrows,
                relations, textures);
        components.add(feature);
        return feature.cooldowns();
    }

    private PlayerTimers registerTimers(Minecraft minecraft, ConfigCatalog configs,
            ClientSession session, ArrowTracker arrows, RelationLookup relations,
            PlayerNameCache playerNames, HudRegistry hudRegistry, TelebowCooldowns telebowCooldowns,
            PlayerEntityTracker players, PlayerEquipmentAccess playerEquipment) {
        TimersConfig config = new TimersConfig();
        configs.register(config);
        TimersFeature feature = new TimersFeature(minecraft, configs, config, session, arrows,
                relations, playerNames, hudRegistry, telebowCooldowns, players, playerEquipment);
        components.add(feature);
        return feature;
    }

    // Category: Misc
    private DenickNames registerDenick(Minecraft minecraft, ConfigCatalog configs,
            ClientSession session, PitContext pitContext, TabPresence presence,
            PlayerNameCache playerNames, NickTracker nicks, PlayerEntityTracker players,
            PlayerEquipmentAccess equipment, CommandRegistry commands, Path path) {
        DenickConfig config = new DenickConfig();
        configs.register(config);
        DenickFeature denick = new DenickFeature(minecraft, client, configs, config, session,
                pitContext, presence, playerNames, nicks, players, equipment, commands, language,
                path, (DenickNamesBinding) minecraft);
        components.add(denick);
        return denick;
    }

    // Features
    // Category: No config
    private void registerDiscordRpc(ConfigCatalog configs, BooleanSetting enabled) {
        components.add(new DiscordRpcFeature(configs, enabled, DiscordIpcConnection::open));
    }

    // Category: Combat
    private void registerAutoClick(Minecraft minecraft, ConfigCatalog configs,
            ClientSession session, AutoClickBinding binding, MinecraftActions actions) {
        AutoClickConfig config = new AutoClickConfig();
        configs.register(config);
        components.add(new AutoClickFeature(minecraft, configs, config, session, binding, actions));
    }

    private void registerSmartBlock(Minecraft minecraft, ConfigCatalog configs,
            ClientSession session, SmartBlockBinding binding) {
        SmartBlockConfig config = new SmartBlockConfig();
        configs.register(config);
        components.add(new SmartBlockFeature(minecraft, configs, config, session, binding));
    }

    // Category: Player
    private void registerAutoFish(ConfigCatalog configs, ClientSession session,
            MinecraftActions actions) {
        AutoFishConfig config = new AutoFishConfig();
        configs.register(config);
        components.add(new AutoFishFeature(configs, config, session, actions));
    }

    private void registerAutoSprint(ConfigCatalog configs, AutoSprintBinding binding) {
        AutoSprintConfig config = new AutoSprintConfig();
        configs.register(config);
        components.add(new AutoSprintFeature(config, binding));
    }

    private void registerChatMacro(Minecraft minecraft, ConfigCatalog configs,
            ClientSession session, SidebarCache sidebar, InputBlocker input,
            CommandRegistry commands, Path path) {
        ChatMacroConfig config = new ChatMacroConfig();
        configs.register(config);
        components.add(new ChatMacroFeature(minecraft, client, configs, config, session, sidebar,
                input, commands, language, path));
    }

    private void registerContractReroll(Minecraft minecraft, ConfigCatalog configs,
            ClientSession session, PitContext pitContext, SidebarCache sidebar, InputBlocker input,
            MinecraftActions actions) {
        ContractRerollConfig config = new ContractRerollConfig();
        configs.register(config);
        components.add(new ContractRerollFeature(minecraft, configs, config, session, pitContext,
                sidebar, input, actions, language));
    }

    private void registerNoJumpDelay(ConfigCatalog configs, NoJumpDelayBinding binding) {
        NoJumpDelayConfig config = new NoJumpDelayConfig();
        configs.register(config);
        components.add(new NoJumpDelayFeature(config, binding));
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

    private void registerItemEsp(ConfigCatalog configs, ClientSession session,
            TextureState textures) {
        ItemEspConfig config = new ItemEspConfig();
        configs.register(config);
        components.add(new ItemEspFeature(configs, config, session, textures));
    }

    private void registerNametag(Minecraft minecraft, ConfigCatalog configs, ClientSession session,
            PlayerEquipmentAccess equipment, DenickNames denickNames, NickTracker nicks,
            PlayerTimers timers) {
        NametagConfig config = new NametagConfig();
        configs.register(config);
        components.add(new NametagFeature(minecraft, configs, config, session, equipment,
                denickNames, nicks, timers, (NametagNamesBinding) minecraft));
    }

    private void registerPlayerEsp(ConfigCatalog configs, ClientSession session,
            TabPresence presence, RelationLookup relations, TextureState textures) {
        PlayerEspConfig config = new PlayerEspConfig();
        configs.register(config);
        components
                .add(new PlayerEspFeature(configs, config, session, presence, relations, textures));
    }

    private void registerPlayerList(ConfigCatalog configs, PlayerNameCache playerNames,
            PlayerEntityTracker players, PlayerEquipmentAccess playerEquipment,
            PitContext pitContext, HudRegistry hudRegistry, RelationLookup relations,
            TabPresence presence, DenickNames denickNames, NickTracker nicks) {
        PlayerListConfig config = new PlayerListConfig();
        configs.register(config);
        components.add(new PlayerListFeature(configs, config, playerNames, players, playerEquipment,
                pitContext, hudRegistry, relations, presence, language, denickNames, nicks));
    }

    private void registerRingHelper(Minecraft minecraft, ConfigCatalog configs,
            ClientSession session, PitContext pitContext, TextureState textures) {
        RingHelperConfig config = new RingHelperConfig();
        configs.register(config);
        components.add(
                new RingHelperFeature(minecraft, configs, config, session, pitContext, textures));
    }

    private void registerTooltip(ConfigCatalog configs) {
        TooltipConfig config = new TooltipConfig();
        configs.register(config);
        components.add(new TooltipFeature(configs, config));
    }

    // Category: Misc
    private void registerAutoQuickMath(ConfigCatalog configs) {
        AutoQuickMathConfig config = new AutoQuickMathConfig();
        configs.register(config);
        components.add(new AutoQuickMathFeature(configs, config));
    }

    private void registerBanAlert(Minecraft minecraft, ConfigCatalog configs, ClientSession session,
            TabPresence presence) {
        BanAlertConfig config = new BanAlertConfig();
        configs.register(config);
        components
                .add(new BanAlertFeature(minecraft, configs, config, session, presence, language));
    }

    // Category: Patch
    private void registerResourceLeakFix(ConfigCatalog configs, ResourceLeakFixBinding binding) {
        ResourceLeakFixConfig config = new ResourceLeakFixConfig();
        configs.register(config);
        components.add(new ResourceLeakFixFeature(config, binding));
    }

    private void registerScoreboardFix(ConfigCatalog configs, ScoreboardFixBinding binding) {
        ScoreboardFixConfig config = new ScoreboardFixConfig();
        configs.register(config);
        components.add(new ScoreboardFixFeature(config, binding));
    }

    public void start() {
        client.check();
        if (started) {
            return;
        }
        try {
            for (ClientLifecycle component : components) {
                // Add it first so we can clean up even if start() fails.
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
        // Consumers still need their dependencies during stop().
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
