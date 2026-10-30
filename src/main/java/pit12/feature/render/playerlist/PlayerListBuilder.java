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
package pit12.feature.render.playerlist;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationLookup;
import pit12.feature.misc.denick.api.DenickNames;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentFormat;
import pit12.runtime.nick.NickTracker;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.PitSnapshot;
import pit12.runtime.pit.SpawnState;
import pit12.runtime.player.PlayerEntityTracker;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentSnapshot;
import pit12.runtime.player.PlayerNameCache;

final class PlayerListBuilder {
    private static final Pattern PRESTIGE_PREFIX = Pattern.compile("\\[(?:§.|[IVXLCDM])+-");
    private final Minecraft minecraft;
    private final PlayerEquipmentAccess equipment;
    private final PlayerEntityTracker players;
    private final PitContext pitContext;
    private final PlayerListConfig config;
    private final RelationLookup relations;
    private final PlayerNameCache playerNames;
    private final DenickNames denickNames;
    private final NickTracker nicks;
    private final Map<UUID, PlayerListEntry> previousEntries = new HashMap<>();
    private WorldClient previousWorld;

    PlayerListBuilder(Minecraft minecraft, PlayerEntityTracker players,
            PlayerEquipmentAccess equipment, PitContext pitContext, PlayerListConfig config,
            RelationLookup relations, PlayerNameCache playerNames, DenickNames denickNames,
            NickTracker nicks) {
        this.minecraft = minecraft;
        this.players = players;
        this.equipment = equipment;
        this.pitContext = pitContext;
        this.config = config;
        this.relations = relations;
        this.playerNames = playerNames;
        this.denickNames = denickNames;
        this.nicks = nicks;
    }

    PlayerListSnapshot build(boolean realNames, boolean rebuildText) {
        WorldClient world = minecraft.theWorld;
        if (world == null || minecraft.getNetHandler() == null) {
            clear();
            return PlayerListSnapshot.empty();
        }
        if (rebuildText || world != previousWorld) {
            previousEntries.clear();
        }
        previousWorld = world;
        EntityPlayer localPlayer = minecraft.thePlayer;
        double forwardX = 0.0D;
        double forwardZ = 0.0D;
        double rightX = 0.0D;
        double rightZ = 0.0D;
        if (config.showDirection() && localPlayer != null) {
            double yaw = Math.toRadians(localPlayer.rotationYaw);
            forwardX = -Math.sin(yaw);
            forwardZ = Math.cos(yaw);
            rightX = -forwardZ;
            rightZ = forwardX;
        }
        EnumMap<PlayerListGroup, List<PlayerListEntry>> groups =
                new EnumMap<>(PlayerListGroup.class);
        PitSnapshot pitSnapshot = pitContext.current();
        for (NetworkPlayerInfo info : minecraft.getNetHandler().getPlayerInfoMap()) {
            UUID playerId = info.getGameProfile().getId();
            if (playerId == null) {
                continue;
            }
            PlayerEquipmentSnapshot playerEquipment = equipment.loadedEquipment(playerId);
            Relation relation = relations.relationOf(playerId);
            PlayerListGroup special = relation == Relation.FRIEND ? PlayerListGroup.FRIEND
                    : relation == Relation.ENEMY ? PlayerListGroup.ENEMY : null;
            PlayerListGroup group = equipmentGroup(playerEquipment);
            boolean showSpecial = special != null && config.showGroup(special);
            boolean nicked = nicks.isNicked(playerId);
            boolean showNick = config.showGroup(PlayerListGroup.NICK) && nicked;
            boolean showEquipment = group != null && config.showGroup(group);
            if (!showSpecial && !showNick && !showEquipment) {
                continue;
            }
            EntityPlayer player = players.loadedPlayer(playerId);
            boolean spawn = (config.hideSpawn() || config.showSpawn()) && player != null
                    && pitSnapshot.spawnStateAt(player.posX, player.posY,
                            player.posZ) == SpawnState.IN_SPAWN;
            if (config.hideSpawn() && spawn) {
                continue;
            }
            PlayerListEntry previous = previousEntries.get(playerId);
            String name = previous != null ? previous.name()
                    : config.shortPlayerNames() ? playerNames.shortName(playerId)
                            : playerNames.displayName(playerId);
            if (name == null) {
                continue;
            }
            if (previous == null) {
                String realName = realNames ? denickNames.realDisplayName(playerId) : null;
                if (realName != null) {
                    name = PRESTIGE_PREFIX.matcher(realName).replaceFirst("[");
                } else if (config.nickMarker() && nicked) {
                    name = name.trim() + " §e§lN";
                }
            }
            boolean distanceKnown = localPlayer != null && player != null;
            boolean directionKnown =
                    config.showDirection() && localPlayer != null && player != null;
            float distance = distanceKnown ? distance(localPlayer, player) : 0.0F;
            float direction = directionKnown
                    ? direction(localPlayer, player, forwardX, forwardZ, rightX, rightZ)
                    : 0.0F;
            String leggingsText = previous != null ? previous.leggingsText()
                    : config.showLeggings() && playerEquipment != null
                            && playerEquipment.leggingsKnown()
                                    ? PitEnchantmentFormat.format(
                                            playerEquipment.leggingsEnchantments(),
                                            config.enchantmentFormat(), config.showGem())
                                    : null;
            String heldItemText = previous != null ? previous.heldItemText()
                    : config.showHeldItem() && playerEquipment != null
                            && playerEquipment.heldItemKnown()
                                    ? PitEnchantmentFormat.format(
                                            playerEquipment.heldEnchantments(),
                                            config.enchantmentFormat(), config.showGem())
                                    : null;
            PlayerListEntry entry =
                    new PlayerListEntry(playerId, player == null ? 0 : player.getEntityId(), name,
                            leggingsText, heldItemText, distance, direction, distanceKnown,
                            directionKnown, config.showSpawn() && spawn);
            previousEntries.put(playerId, entry);
            if (showSpecial) {
                add(groups, special, entry);
            }
            if (showNick) {
                add(groups, PlayerListGroup.NICK, entry);
            }
            if (showEquipment) {
                add(groups, group, entry);
            }
        }
        return PlayerListSnapshot.create(groups);
    }

    void clear() {
        previousEntries.clear();
        previousWorld = null;
    }

    private static void add(Map<PlayerListGroup, List<PlayerListEntry>> groups,
            PlayerListGroup group, PlayerListEntry entry) {
        groups.computeIfAbsent(group, key -> new ArrayList<>()).add(entry);
    }

    private static PlayerListGroup equipmentGroup(PlayerEquipmentSnapshot equipment) {
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
