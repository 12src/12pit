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
package pit12.feature.render.focus;

import static net.minecraft.util.EnumChatFormatting.AQUA;
import static net.minecraft.util.EnumChatFormatting.GRAY;
import static net.minecraft.util.EnumChatFormatting.GREEN;
import static pit12.runtime.languages.Languages.source;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationLookup;
import pit12.feature.client.relation.api.RelationReadiness;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.input.InputBlocker;
import pit12.runtime.input.InputHandler;
import pit12.runtime.languages.Languages;
import pit12.runtime.player.PlayerNameCache;
import pit12.shared.chat.ChatFeedback;
import pit12.shared.chat.ChatFeedback.Tone;
import pit12.shared.entity.HurtTimeAccess;
import pit12.shared.input.Keybind;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.performance.HotPath;

public final class FocusFeature implements ClientLifecycle, ConfigChangeListener, InputHandler {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final FocusConfig config;
    private final InputBlocker input;
    private final RelationLookup relations;
    private final PlayerNameCache playerNames;
    private final Languages language;
    private final FocusBinding binding;
    private boolean started;
    private boolean enabled;
    private boolean active;
    private boolean adjustingTimers;
    private boolean trackingTeams;
    private int mode;
    private float opacity;
    private int equipment;
    private boolean matchAll;
    private int toggleKey;
    private int cycleKey;

    public FocusFeature(Minecraft minecraft, ConfigCatalog configs, FocusConfig config,
            InputBlocker input, RelationLookup relations, PlayerNameCache playerNames,
            Languages language, FocusBinding binding) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.input = input;
        this.relations = relations;
        this.playerNames = playerNames;
        this.language = language;
        this.binding = binding;
    }

    @Override
    public void start() {
        if (started)
            return;
        started = true;
        configs.addListener(this);
        input.addHandler(this);
        updateSettings();
        binding.pit12$bindFocus(this);
    }

    @Override
    public void stop() {
        started = false;
        enabled = false;
        active = false;
        FocusRenderPass.enabled = false;
        FocusRenderState.releaseDepthBuffers();
        if (adjustingTimers) {
            adjustingTimers = false;
            MinecraftForge.EVENT_BUS.unregister(this);
        }
        if (trackingTeams) {
            trackingTeams = false;
            playerNames.release(this);
        }
        binding.pit12$bindFocus(null);
        input.removeHandler(this);
        configs.removeListener(this);
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (started && (changes.affects("focus", "enabled") || changes.affects("focus", "mode")
                || changes.affects("focus", "opacity") || changes.affects("focus", "toggle_key")
                || changes.affects("focus", "cycle_key") || changes.affects("focus", "chestplate")
                || changes.affects("focus", "boots") || changes.affects("focus", "leggings")
                || changes.affects("focus", "helmet") || changes.affects("focus", "target_priority")
                || changes.affects("focus", "equipment_match"))) {
            updateSettings();
        }
    }

    private void updateSettings() {
        enabled = config.enabled();
        mode = config.mode.get();
        opacity = config.opacity.get() * 0.01F;
        equipment = (config.chestplate.get() ? 1 : 0) | (config.boots.get() ? 2 : 0)
                | (config.leggings.get() ? 4 : 0) | (config.helmet.get() ? 8 : 0);
        matchAll = config.equipmentMatch.get() == 0;
        toggleKey = config.toggleKey.get();
        cycleKey = config.cycleKey.get();
        boolean selecting = started && enabled && (mode != 0 || equipment != 0);
        active = selecting && opacity < 1.0F;
        FocusRenderPass.enabled = active && opacity > 0.0F;
        if (!FocusRenderPass.enabled)
            FocusRenderState.releaseDepthBuffers();
        boolean nextAdjusting = selecting && config.targetPriority.get();
        if (adjustingTimers != nextAdjusting) {
            adjustingTimers = nextAdjusting;
            if (adjustingTimers)
                MinecraftForge.EVENT_BUS.register(this);
            else
                MinecraftForge.EVENT_BUS.unregister(this);
        }
        boolean nextTracking = (active || adjustingTimers) && (mode == 1 || mode == 3);
        if (trackingTeams != nextTracking) {
            trackingTeams = nextTracking;
            if (trackingTeams)
                playerNames.retain(this);
            else
                playerNames.release(this);
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.END || minecraft.theWorld == null || minecraft.thePlayer == null)
            return;
        for (EntityPlayer player : minecraft.theWorld.playerEntities) {
            if (player != minecraft.thePlayer && isNonTarget(player)) {
                // Leave room for the timers to tick down before other clients read them.
                ((HurtTimeAccess) player).pit12$overrideHurtTime(12, 12 + 10);
            }
        }
    }

    @Override
    public boolean consumesKey(int key) {
        return Keybind.matches(toggleKey, key) || enabled && Keybind.matches(cycleKey, key);
    }

    @Override
    public boolean key(int key, boolean pressed, boolean repeat) {
        if (!pressed || repeat || minecraft.currentScreen != null || !minecraft.inGameHasFocus
                || minecraft.thePlayer == null)
            return false;
        boolean toggle = Keybind.matches(toggleKey, key);
        if (!toggle && (!enabled || !Keybind.matches(cycleKey, key)))
            return false;
        int previousMode = mode;
        if (toggle) {
            config.setEnabled(!enabled);
        } else {
            config.mode.set((mode + 1) % config.mode.choices().size());
        }
        String name = Tone.INFO.argument(AQUA, config.mode.choices().get(mode).displayName());
        ChatFeedback.reply(minecraft.thePlayer, Tone.INFO, toggle
                ? language.format(source("Focus {0}: {1}"),
                        Tone.INFO.argument(enabled ? GREEN : GRAY,
                                language.translate(
                                        enabled ? source("enabled") : source("disabled"))),
                        name)
                : language.format(source("Focus mode: {0} -> {1}"), Tone.INFO.argument(GRAY,
                        config.mode.choices().get(previousMode).displayName()), name));
        return true;
    }

    @HotPath
    public float opacity(EntityPlayer player) {
        if (!active || player == minecraft.thePlayer || minecraft.thePlayer == null
                || player.worldObj != minecraft.theWorld)
            return 1.0F;
        return isNonTarget(player) ? opacity : 1.0F;
    }

    private boolean isNonTarget(EntityPlayer player) {
        switch (mode) {
            case 0:
                return isDiamond(player);
            case 1:
            case 3:
                int ownColor = playerNames.teamNameColor(minecraft.thePlayer.getUniqueID());
                if (ownColor < 0)
                    return false;
                int color = playerNames.teamNameColor(player.getUniqueID());
                if (color < 0)
                    return false;
                return color == ownColor || mode == 3 && isDiamond(player);
            default:
                return relations.readiness() == RelationReadiness.READY
                        && relations.relationOf(player.getUniqueID()) != Relation.ENEMY;
        }
    }

    private boolean isDiamond(EntityPlayer player) {
        if (equipment == 0)
            return false;
        if (matchAll) {
            return ((equipment & 1) == 0 || wears(player, 2, Items.diamond_chestplate))
                    && ((equipment & 2) == 0 || wears(player, 0, Items.diamond_boots))
                    && ((equipment & 4) == 0 || wears(player, 1, Items.diamond_leggings))
                    && ((equipment & 8) == 0 || matchesHelmet(player));
        }
        return (equipment & 1) != 0 && wears(player, 2, Items.diamond_chestplate)
                || (equipment & 2) != 0 && wears(player, 0, Items.diamond_boots)
                || (equipment & 4) != 0 && wears(player, 1, Items.diamond_leggings)
                || (equipment & 8) != 0 && matchesHelmet(player);
    }

    private static boolean matchesHelmet(EntityPlayer player) {
        ItemStack helmet = player.getCurrentArmor(3);
        return helmet != null && (helmet.getItem() == Items.diamond_helmet
                || helmet.getItem() == Items.golden_helmet);
    }

    private static boolean wears(EntityPlayer player, int slot, Item item) {
        ItemStack stack = player.getCurrentArmor(slot);
        return stack != null && stack.getItem() == item;
    }
}
