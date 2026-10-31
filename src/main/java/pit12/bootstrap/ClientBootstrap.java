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
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.client.FMLClientHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pit12.feature.client.discordrpc.DiscordRpcFeature;
import pit12.feature.client.hudeditor.HudEditorFeature;
import pit12.feature.client.profile.ProfilesFeature;
import pit12.feature.client.profile.api.Profiles;
import pit12.feature.client.relation.RelationFeature;
import pit12.feature.client.relation.api.RelationLookup;
import pit12.feature.client.relation.api.Relations;
import pit12.feature.client.webui.WebUiConfig;
import pit12.feature.client.webui.WebUiFeature;
import pit12.feature.combat.nojumpdelay.NoJumpDelayBinding;
import pit12.feature.combat.nojumpdelay.NoJumpDelayConfig;
import pit12.feature.combat.nojumpdelay.NoJumpDelayFeature;
import pit12.feature.combat.smartblock.SmartBlockBinding;
import pit12.feature.combat.smartblock.SmartBlockConfig;
import pit12.feature.combat.smartblock.SmartBlockFeature;
import pit12.feature.combat.sprint.AutoSprintBinding;
import pit12.feature.combat.sprint.AutoSprintConfig;
import pit12.feature.combat.sprint.AutoSprintFeature;
import pit12.feature.combat.swap.SwapConfig;
import pit12.feature.combat.swap.SwapFeature;
import pit12.feature.combat.swap.SwapHooksBinding;
import pit12.feature.combat.swap.api.SwapBindings;
import pit12.feature.misc.autofish.AutoFishConfig;
import pit12.feature.misc.autofish.AutoFishFeature;
import pit12.feature.misc.banalert.BanAlertConfig;
import pit12.feature.misc.banalert.BanAlertFeature;
import pit12.feature.misc.chatmacro.ChatMacroConfig;
import pit12.feature.misc.chatmacro.ChatMacroFeature;
import pit12.feature.misc.contractreroll.ContractRerollConfig;
import pit12.feature.misc.contractreroll.ContractRerollFeature;
import pit12.feature.misc.denick.DenickConfig;
import pit12.feature.misc.denick.DenickFeature;
import pit12.feature.misc.denick.DenickNamesBinding;
import pit12.feature.misc.denick.api.DenickNames;
import pit12.feature.misc.quickmath.AutoQuickMathConfig;
import pit12.feature.misc.quickmath.AutoQuickMathFeature;
import pit12.feature.patch.commandfix.CommandFixBinding;
import pit12.feature.patch.commandfix.CommandFixConfig;
import pit12.feature.patch.commandfix.CommandFixFeature;
import pit12.feature.patch.fontcachefix.FontCacheFixBinding;
import pit12.feature.patch.fontcachefix.FontCacheFixConfig;
import pit12.feature.patch.fontcachefix.FontCacheFixFeature;
import pit12.feature.patch.resourceleakfix.ResourceLeakFixBinding;
import pit12.feature.patch.resourceleakfix.ResourceLeakFixConfig;
import pit12.feature.patch.resourceleakfix.ResourceLeakFixFeature;
import pit12.feature.patch.scoreboardfix.ScoreboardFixBinding;
import pit12.feature.patch.scoreboardfix.ScoreboardFixConfig;
import pit12.feature.patch.scoreboardfix.ScoreboardFixFeature;
import pit12.feature.render.eventlist.EventListConfig;
import pit12.feature.render.eventlist.EventListFeature;
import pit12.feature.render.gamma.GammaBinding;
import pit12.feature.render.gamma.GammaConfig;
import pit12.feature.render.gamma.GammaFeature;
import pit12.feature.render.itemesp.ItemEspConfig;
import pit12.feature.render.itemesp.ItemEspFeature;
import pit12.feature.render.nametag.NametagConfig;
import pit12.feature.render.nametag.NametagFeature;
import pit12.feature.render.nametag.NametagNamesBinding;
import pit12.feature.render.playeresp.PlayerEspConfig;
import pit12.feature.render.playeresp.PlayerEspFeature;
import pit12.feature.render.playerlist.PlayerListConfig;
import pit12.feature.render.playerlist.PlayerListFeature;
import pit12.feature.render.ringhelper.RingHelperConfig;
import pit12.feature.render.ringhelper.RingHelperFeature;
import pit12.feature.render.telebow.TelebowConfig;
import pit12.feature.render.telebow.TelebowFeature;
import pit12.feature.render.telebow.api.TelebowCooldowns;
import pit12.feature.render.timers.TimersConfig;
import pit12.feature.render.timers.TimersFeature;
import pit12.feature.render.timers.api.PlayerTimers;
import pit12.feature.render.tooltip.TooltipConfig;
import pit12.feature.render.tooltip.TooltipFeature;
import pit12.platform.discord.DiscordIpcConnection;
import pit12.platform.forge.ForgeCommandAdapter;
import pit12.platform.lunar.LunarSupport;
import pit12.platform.lunar.LunarTextureState;
import pit12.platform.mixinconfig.MixinFailureWarning;
import pit12.platform.oneconfig.OneConfigSupport;
import pit12.runtime.arrow.ArrowTracker;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.input.InputBlockBinding;
import pit12.runtime.input.InputBlocker;
import pit12.runtime.input.InputTickBinding;
import pit12.runtime.input.InputTicks;
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
import pit12.shared.concurrent.ClientThread;
import pit12.shared.input.MinecraftActions;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.lifecycle.ClientShutdownBinding;
import pit12.shared.rendering.OpenGlTextureState;
import pit12.shared.rendering.TextureState;

public final class ClientBootstrap {
    private static final Logger LOGGER = LogManager.getLogger(ClientBootstrap.class);
    private final List<ClientLifecycle> components = new ArrayList<>();
    private final List<ClientLifecycle> startedComponents = new ArrayList<>();
    private final ClientThread client;
    private final Languages language;
    private final ClientShutdownBinding shutdown;
    private boolean started;

    public ClientBootstrap() {
        // BootstrapLayoutTest reads the section and category comments.
        // Shared runtime
        Minecraft minecraft = Minecraft.getMinecraft();
        client = new ClientThread(minecraft::isCallingFromMinecraftThread,
                minecraft::addScheduledTask);
        language = new Languages(client);
        ConfigCatalog configs = new ConfigCatalog(client);
        ConfigCatalog webUiConfigs = new ConfigCatalog(client);
        shutdown = (ClientShutdownBinding) minecraft;
        ClientSession session =
                new ClientSession(minecraft, client, (ClientSessionBinding) minecraft);
        components.add(session);
        ArrowTracker arrows = new ArrowTracker(minecraft, session);
        components.add(arrows);
        InputBlocker input = new InputBlocker(minecraft, client, (InputBlockBinding) minecraft);
        components.add(input);
        InputTicks inputTicks = new InputTicks(client, (InputTickBinding) minecraft);
        components.add(inputTicks);
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
        HudRegistry hudRegistry = new HudRegistry(client);
        Supplier<TextureState> textureStates =
                LunarSupport.isLoaded() ? LunarTextureState::new : OpenGlTextureState::new;
        // Feature providers
        // Category: Client
        Profiles profiles =
                registerProfiles(configs, new File(minecraft.mcDataDir, "12pit/config").toPath());
        HudEditorFeature hudEditor = registerHudEditor(hudRegistry, commands, configs, profiles);
        Relations relations = registerRelations(presence,
                new File(minecraft.mcDataDir, "12pit/relations.json").toPath(), client, commands);
        // Category: Combat
        SwapBindings swapBindings = registerSwap(minecraft, client, configs, session, pitContext,
                playerEquipment, relations, commands,
                new File(minecraft.mcDataDir, "12pit/swap-bindings.json").toPath(),
                (SwapHooksBinding) minecraft, input, inputTicks);
        // Category: Client
        BooleanSetting discordRpc =
                registerWebUi(webUiConfigs, configs, profiles, relations, hudEditor, swapBindings,
                        commands, new File(minecraft.mcDataDir, "12pit/webui.json").toPath());
        // Category: Render
        TelebowCooldowns telebowCooldowns = registerTelebow(minecraft, configs, session, arrows,
                relations, textureStates.get(), players, pitContext);
        PlayerTimers timers = registerTimers(minecraft, configs, session, arrows, relations,
                playerNames, hudRegistry, telebowCooldowns, players, playerEquipment, pitContext);
        // Category: Misc
        DenickNames denickNames = registerDenick(minecraft, configs, session, pitContext, presence,
                playerNames, nicks, players, playerEquipment, commands,
                new File(minecraft.mcDataDir, "12pit/denick-cache.json").toPath());
        // Features
        // Category: Client
        registerDiscordRpc(webUiConfigs, discordRpc);
        // Category: Combat
        registerAutoSprint(configs, (AutoSprintBinding) minecraft);
        registerNoJumpDelay(configs, (NoJumpDelayBinding) minecraft);
        registerSmartBlock(minecraft, configs, session, (SmartBlockBinding) minecraft);
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
        registerAutoFish(configs, session, (MinecraftActions) minecraft);
        registerAutoQuickMath(configs);
        registerBanAlert(minecraft, configs, session, presence);
        registerChatMacro(minecraft, configs, session, sidebar, input, commands,
                new File(minecraft.mcDataDir, "12pit/chat-macros.json").toPath());
        registerContractReroll(minecraft, configs, session, pitContext, sidebar, input,
                (MinecraftActions) minecraft);
        // Category: Patch
        registerCommandFix(configs, (CommandFixBinding) ClientCommandHandler.instance);
        if (minecraft instanceof FontCacheFixBinding) {
            registerFontCacheFix(configs, (FontCacheFixBinding) minecraft);
        }
        if (FMLClientHandler.instance().hasOptifine()) {
            registerResourceLeakFix(configs,
                    (ResourceLeakFixBinding) minecraft.getTextureMapBlocks());
        }
        registerScoreboardFix(configs, (ScoreboardFixBinding) minecraft);
        // Platform integrations
        components.add(new MixinFailureWarning(minecraft, session, language));
        OneConfigSupport oneConfig = new OneConfigSupport(configs, hudRegistry);
        components.add(oneConfig);
        language.addListener(() -> configs.localize(language));
        language.addListener(() -> webUiConfigs.localize(language));
        webUiConfigs.freeze();
        configs.freeze();
    }

    // Feature providers
    // Category: Client
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
            CommandRegistry commands, Path path, SwapHooksBinding binding, InputBlocker input,
            InputTicks inputTicks) {
        SwapConfig config = new SwapConfig();
        configs.register(config);
        SwapFeature swap = new SwapFeature(minecraft, client, configs, config, session,
                playerEquipment, relations, commands, path, binding, input, inputTicks, language);
        components.add(swap);
        return swap.bindings();
    }

    // Category: Client
    private BooleanSetting registerWebUi(ConfigCatalog configs, ConfigCatalog featureConfigs,
            Profiles profiles, Relations relations, HudEditorFeature hudEditor,
            SwapBindings swapBindings, CommandRegistry commands, Path path) {
        WebUiConfig config = new WebUiConfig();
        configs.register(config);
        WebUiFeature webUi = new WebUiFeature(featureConfigs, configs, profiles, relations,
                hudEditor, swapBindings, config, commands, language, path);
        hudEditor.setWebUiOpener(config.keybind()::get, webUi::open);
        components.add(webUi);
        return config.discordRpc();
    }

    // Category: Render
    private TelebowCooldowns registerTelebow(Minecraft minecraft, ConfigCatalog configs,
            ClientSession session, ArrowTracker arrows, RelationLookup relations,
            TextureState textures, PlayerEntityTracker players, PitContext pit) {
        TelebowConfig config = new TelebowConfig();
        configs.register(config);
        TelebowFeature feature = new TelebowFeature(minecraft, configs, config, session, arrows,
                relations, textures, players, pit);
        components.add(feature);
        return feature.cooldowns();
    }

    private PlayerTimers registerTimers(Minecraft minecraft, ConfigCatalog configs,
            ClientSession session, ArrowTracker arrows, RelationLookup relations,
            PlayerNameCache playerNames, HudRegistry hudRegistry, TelebowCooldowns telebowCooldowns,
            PlayerEntityTracker players, PlayerEquipmentAccess playerEquipment, PitContext pit) {
        TimersConfig config = new TimersConfig();
        configs.register(config);
        TimersFeature feature =
                new TimersFeature(minecraft, configs, config, session, arrows, relations,
                        playerNames, hudRegistry, telebowCooldowns, players, playerEquipment, pit);
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
    // Category: Client
    private void registerDiscordRpc(ConfigCatalog configs, BooleanSetting enabled) {
        components.add(new DiscordRpcFeature(configs, enabled, DiscordIpcConnection::open));
    }

    // Category: Combat
    private void registerAutoSprint(ConfigCatalog configs, AutoSprintBinding binding) {
        AutoSprintConfig config = new AutoSprintConfig();
        configs.register(config);
        components.add(new AutoSprintFeature(config, binding));
    }

    private void registerNoJumpDelay(ConfigCatalog configs, NoJumpDelayBinding binding) {
        NoJumpDelayConfig config = new NoJumpDelayConfig();
        configs.register(config);
        components.add(new NoJumpDelayFeature(config, binding));
    }

    private void registerSmartBlock(Minecraft minecraft, ConfigCatalog configs,
            ClientSession session, SmartBlockBinding binding) {
        SmartBlockConfig config = new SmartBlockConfig();
        configs.register(config);
        components.add(new SmartBlockFeature(minecraft, configs, config, session, binding));
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
    private void registerAutoFish(ConfigCatalog configs, ClientSession session,
            MinecraftActions actions) {
        AutoFishConfig config = new AutoFishConfig();
        configs.register(config);
        components.add(new AutoFishFeature(configs, config, session, actions));
    }

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

    // Category: Patch
    private void registerCommandFix(ConfigCatalog configs, CommandFixBinding binding) {
        CommandFixConfig config = new CommandFixConfig();
        configs.register(config);
        components.add(new CommandFixFeature(config, binding));
    }

    private void registerFontCacheFix(ConfigCatalog configs, FontCacheFixBinding binding) {
        FontCacheFixConfig config = new FontCacheFixConfig();
        configs.register(config);
        components.add(new FontCacheFixFeature(config, binding));
    }

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
                LOGGER.warn("Failed to stop component {}", component.getClass().getName(), failure);
            }
        }
        startedComponents.clear();
    }
}
