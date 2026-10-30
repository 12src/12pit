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
package pit12.feature.render.nametag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.misc.denick.api.DenickNames;
import pit12.feature.render.timers.api.PlayerTimers;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.item.PitEnchantmentFormat;
import pit12.runtime.nick.NickTracker;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentCache;
import pit12.runtime.player.PlayerEquipmentListener;
import pit12.runtime.player.PlayerEquipmentSnapshot;
import pit12.runtime.session.ClientSession;
import pit12.shared.lifecycle.ClientLifecycle;

public final class NametagFeature
        implements ClientLifecycle, ConfigChangeListener, PlayerEquipmentListener {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final NametagConfig config;
    private final ClientSession session;
    private final PlayerEquipmentAccess equipment;
    private final DenickNames denickNames;
    private final NickTracker nicks;
    private final PlayerTimers timers;
    private final NametagNamesBinding binding;
    private final NametagRenderer renderer = new NametagRenderer();
    private final Map<UUID, NametagText> texts = new HashMap<>();
    private final Runnable sessionChanged = this::rebuildTexts;
    private boolean unicodeFont;
    private boolean fontDirty;
    private boolean started;
    private boolean active;
    private int timerTypes;

    public NametagFeature(Minecraft minecraft, ConfigCatalog configs, NametagConfig config,
            ClientSession session, PlayerEquipmentAccess equipment, DenickNames denickNames,
            NickTracker nicks, PlayerTimers timers, NametagNamesBinding binding) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.equipment = equipment;
        this.denickNames = denickNames;
        this.nicks = nicks;
        this.timers = timers;
        this.binding = binding;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started) {
            return;
        }
        started = true;
        configs.addListener(this);
        updateActivation();
    }

    @Override
    public void stop() {
        session.checkThread();
        if (!started) {
            return;
        }
        started = false;
        configs.removeListener(this);
        updateActivation();
    }

    private void updateActivation() {
        boolean enabled = started && config.enabled();
        timerTypes = enabled ? config.timerTypes() : 0;
        binding.pit12$bindNametagNames(
                enabled && (config.nickMarker() || config.realNames() || timerTypes != 0)
                        ? this::nameSuffix
                        : null);
        if (enabled && (config.showHeldItem() || config.showLeggings())) {
            if (!active) {
                active = true;
                try {
                    equipment.addListener(this);
                    session.addListener(sessionChanged);
                    MinecraftForge.EVENT_BUS.register(this);
                    rebuildTexts();
                } catch (RuntimeException failure) {
                    deactivate();
                    throw failure;
                }
            }
        } else {
            deactivate();
        }
    }

    private void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        session.removeListener(sessionChanged);
        equipment.removeListener(this);
        texts.clear();
        fontDirty = false;
    }

    private void rebuildTexts() {
        texts.clear();
        unicodeFont = minecraft.fontRendererObj.getUnicodeFlag();
        fontDirty = false;
        WorldClient world = session.world();
        if (world != null) {
            for (EntityPlayer player : world.playerEntities) {
                updateText(player.getUniqueID());
            }
        }
    }

    private void updateText(UUID playerId) {
        PlayerEquipmentSnapshot snapshot = equipment.loadedEquipment(playerId);
        if (snapshot == null) {
            texts.remove(playerId);
            return;
        }
        String heldItem = config.showHeldItem() && snapshot.heldItemKnown() ? PitEnchantmentFormat
                .format(snapshot.heldEnchantments(), config.enchantmentFormat(), config.showGem())
                : null;
        String leggings =
                config.showLeggings() && snapshot.leggingsKnown()
                        ? PitEnchantmentFormat.format(snapshot.leggingsEnchantments(),
                                config.enchantmentFormat(), config.showGem())
                        : null;
        if (heldItem == null && leggings == null) {
            texts.remove(playerId);
        } else {
            texts.put(playerId, new NametagText(heldItem, leggings, minecraft.fontRendererObj));
        }
    }

    @Override
    public void onPlayerEquipmentChanged(UUID playerId, int changedSlots, long revision) {
        if (active
                && ((config.showHeldItem() && (changedSlots & PlayerEquipmentCache.HELD_ITEM) != 0)
                        || (config.showLeggings()
                                && (changedSlots & PlayerEquipmentCache.LEGGINGS) != 0))) {
            updateText(playerId);
        }
    }

    @Override
    public void onPlayerEquipmentRemoved(UUID playerId) {
        texts.remove(playerId);
    }

    @Override
    public void onPlayerEquipmentReset() {
        texts.clear();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started) {
            return;
        }
        if (changes.affects("nametag", "enabled") || changes.affects("nametag", "nick_marker")
                || changes.affects("nametag", "real_names")
                || changes.affects("nametag", "show_held_item")
                || changes.affects("nametag", "show_leggings")
                || changes.affects("nametag", "pin_timer")
                || changes.affects("nametag", "venom_timer")
                || changes.affects("nametag", "jump_boost_timer")
                || changes.affects("nametag", "telebow_timer")) {
            updateActivation();
        }
        if (active && (changes.affects("nametag", "enchantment_format")
                || changes.affects("nametag", "show_gem")
                || changes.affects("nametag", "show_held_item")
                || changes.affects("nametag", "show_leggings"))) {
            rebuildTexts();
        }
    }

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Post event) {
        if (active) {
            fontDirty = true;
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (!active || event.phase != Phase.END) {
            return;
        }
        session.checkThread();
        if (fontDirty || unicodeFont != minecraft.fontRendererObj.getUnicodeFlag()) {
            rebuildTexts();
        }
    }

    private String nameSuffix(UUID playerId) {
        session.checkThread();
        String suffix = config.nickMarker() && nicks.isNicked(playerId) ? " §e§lN" : "";
        if (config.realNames()) {
            String realName = denickNames.realDisplayName(playerId);
            if (realName != null) {
                suffix += " §7(" + realName + "§7)";
            }
        }
        return timerTypes == 0 ? suffix : suffix + timers.countdowns(playerId, timerTypes);
    }

    // Lunar replaces name rendering and skips Forge's Specials events.
    @SubscribeEvent
    public void onRenderName(RenderLivingEvent.Post<?> event) {
        if (!active || !(event.entity instanceof EntityPlayer) || minecraft.thePlayer == null
                || event.entity == minecraft.thePlayer
                || event.entity.worldObj != minecraft.theWorld
                || session.world() != minecraft.theWorld || !Minecraft.isGuiEnabled()) {
            return;
        }
        Entity camera = minecraft.getRenderViewEntity();
        if (camera == null || event.entity == camera) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.entity;
        double distanceSquared = player.getDistanceSqToEntity(camera);
        float nameRange = player.isSneaking() ? RendererLivingEntity.NAME_TAG_RANGE_SNEAK
                : RendererLivingEntity.NAME_TAG_RANGE;
        if (distanceSquared >= nameRange * nameRange
                || player.isInvisibleToPlayer(minecraft.thePlayer)) {
            return;
        }
        NametagText text = texts.get(player.getUniqueID());
        // The model can render even when its nametag is hidden.
        if (text != null && ((NametagVisibility) event.renderer).pit12$canRenderName(player)) {
            renderer.render(minecraft, player, text, event.x, event.y, event.z, distanceSquared,
                    config.upwardOffset());
        }
    }
}
