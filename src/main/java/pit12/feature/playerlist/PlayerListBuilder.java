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
package pit12.feature.playerlist;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantments;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.PitSnapshot;
import pit12.runtime.pit.SpawnState;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentSnapshot;

final class PlayerListBuilder {
    private final Minecraft minecraft;
    private final PlayerEquipmentAccess equipment;
    private final PitContext pitContext;
    private final PlayerListConfig config;

    PlayerListBuilder(Minecraft minecraft, PlayerEquipmentAccess equipment, PitContext pitContext,
            PlayerListConfig config) {
        this.minecraft = minecraft;
        this.equipment = equipment;
        this.pitContext = pitContext;
        this.config = config;
    }

    PlayerListSnapshot build() {
        WorldClient world = minecraft.theWorld;
        if (world == null || minecraft.getNetHandler() == null) {
            return PlayerListSnapshot.empty();
        }
        EntityPlayer localPlayer = minecraft.thePlayer;
        double forwardX = 0.0D;
        double forwardZ = 0.0D;
        double rightX = 0.0D;
        double rightZ = 0.0D;
        if (config.showDirection() && localPlayer != null) {
            double yaw = Math.toRadians(localPlayer.rotationYaw);
            forwardX = -Math.sin(yaw);
            forwardZ = Math.cos(yaw);
            rightX = Math.cos(yaw);
            rightZ = Math.sin(yaw);
        }
        EnumMap<PlayerListGroup, List<PlayerListEntry>> groups =
                new EnumMap<PlayerListGroup, List<PlayerListEntry>>(PlayerListGroup.class);
        int playerCount = 0;
        PitSnapshot pitSnapshot = pitContext.current();
        Collection<NetworkPlayerInfo> tabPlayers = minecraft.getNetHandler().getPlayerInfoMap();
        for (NetworkPlayerInfo info : tabPlayers) {
            UUID playerId = info.getGameProfile().getId();
            if (playerId == null) {
                continue;
            }
            PlayerEquipmentSnapshot playerEquipment = equipment.loadedEquipment(playerId);
            PlayerListGroup group = groupOf(playerEquipment);
            if (group == null || !config.showGroup(group)) {
                continue;
            }
            EntityPlayer player = world.getPlayerEntityByUUID(playerId);
            boolean distanceKnown = localPlayer != null && player != null;
            boolean directionKnown =
                    config.showDirection() && localPlayer != null && player != null;
            float distance = distanceKnown ? distance(localPlayer, player) : 0.0F;
            float direction = directionKnown
                    ? direction(localPlayer, player, forwardX, forwardZ, rightX, rightZ)
                    : 0.0F;
            boolean spawn = config.showSpawn() && player != null && pitSnapshot
                    .spawnStateAt(player.posX, player.posY, player.posZ) == SpawnState.IN_SPAWN;
            String leggingsText = config.showLeggings() && playerEquipment.leggingsKnown()
                    ? formatEnchantments(playerEquipment.leggingsEnchantments())
                    : null;
            String heldItemText = config.showHeldItem() && playerEquipment.heldItemKnown()
                    ? formatEnchantments(playerEquipment.heldEnchantments())
                    : null;
            PlayerListEntry entry = new PlayerListEntry(playerId,
                    player == null ? 0 : player.getEntityId(), nameOf(info), group, leggingsText,
                    heldItemText, distance, direction, distanceKnown, directionKnown, spawn);
            List<PlayerListEntry> groupEntries = groups.get(group);
            if (groupEntries == null) {
                groupEntries = new java.util.ArrayList<PlayerListEntry>();
                groups.put(group, groupEntries);
            }
            groupEntries.add(entry);
            playerCount++;
        }
        return PlayerListSnapshot.create(groups, playerCount);
    }

    private String formatEnchantments(PitEnchantments enchantments) {
        switch (config.enchantmentFormat()) {
            case PlayerListConfig.ENCHANTMENT_FORMAT_PLAIN_LEVELS:
                return enchantments.formatDisplayNames();
            case PlayerListConfig.ENCHANTMENT_FORMAT_HIDE_LEVEL_THREE:
                return enchantments.formatBoldDisplayNamesWithoutLevelThree();
            case PlayerListConfig.ENCHANTMENT_FORMAT_NAMES_ONLY:
                return enchantments.formatBoldDisplayNamesWithoutLevels();
            case PlayerListConfig.ENCHANTMENT_FORMAT_BOLD_LEVELS:
            default:
                return enchantments.formatBoldDisplayNames();
        }
    }

    long tabSignature() {
        if (minecraft.getNetHandler() == null) {
            return 0L;
        }
        long signature = 1L;
        for (NetworkPlayerInfo info : minecraft.getNetHandler().getPlayerInfoMap()) {
            UUID id = info.getGameProfile().getId();
            signature = 31L * signature + (id == null ? 0 : id.hashCode());
            signature = 31L * signature + nameOf(info).hashCode();
        }
        return signature;
    }

    private static PlayerListGroup groupOf(PlayerEquipmentSnapshot equipment) {
        if (equipment == null || !equipment.leggingsKnown()) {
            return null;
        }
        if (equipment.leggingsEnchantments().contains(PitEnchantment.Regularity)) {
            return PlayerListGroup.REGULARITY;
        }
        if (equipment.leggingsEnchantments().contains(PitEnchantment.Somber)) {
            return PlayerListGroup.DARK;
        }
        return equipment.hasGoldenLeggings() ? PlayerListGroup.BOUNTY_HUNTER : null;
    }

    private String nameOf(NetworkPlayerInfo info) {
        String name = minecraft.ingameGUI.getTabList().getPlayerName(info);
        int end = name.length();
        while (end > 0 && Character.isWhitespace(name.charAt(end - 1))) {
            end--;
        }
        return end == name.length() ? name : name.substring(0, end);
    }

    private static float distance(EntityPlayer localPlayer, EntityPlayer player) {
        double dx = player.posX - localPlayer.posX;
        double dy = player.posY - localPlayer.posY;
        double dz = player.posZ - localPlayer.posZ;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float direction(EntityPlayer localPlayer, EntityPlayer player, double forwardX,
            double forwardZ, double rightX, double rightZ) {
        double dx = player.posX - localPlayer.posX;
        double dz = player.posZ - localPlayer.posZ;
        double forward = dx * forwardX + dz * forwardZ;
        double right = dx * rightX + dz * rightZ;
        return (float) Math.toDegrees(Math.atan2(right, forward));
    }
}
