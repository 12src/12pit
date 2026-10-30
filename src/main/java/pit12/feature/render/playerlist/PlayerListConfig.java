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

import static pit12.runtime.languages.Languages.source;

import java.util.EnumMap;
import java.util.Map;
import org.lwjgl.input.Keyboard;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.HudAnchor;
import pit12.runtime.config.HudConfig;
import pit12.runtime.config.HudGroupConfig;
import pit12.runtime.config.KeybindSetting;
import pit12.runtime.item.PitEnchantmentFormat;
import pit12.shared.text.TextCodes;

public final class PlayerListConfig extends FeatureConfig {
    final Map<PlayerListGroup, HudGroupConfig> hudGroups = new EnumMap<>(PlayerListGroup.class);
    private final BooleanSetting showHeldItem;
    private final BooleanSetting showLeggings;
    private final ChoiceSetting enchantmentFormat;
    private final BooleanSetting showGem;
    private final BooleanSetting shortPlayerNames;
    private final BooleanSetting nickMarker;
    private final KeybindSetting realNamesKey;
    private final BooleanSetting showDistance;
    private final BooleanSetting showDirection;
    private final BooleanSetting showSpawn;
    private final BooleanSetting hideSpawn;
    private final BooleanSetting showGroupName;
    private final BooleanSetting showFriend;
    private final BooleanSetting showEnemy;
    private final BooleanSetting showNick;
    private final BooleanSetting showRegularity;
    private final BooleanSetting showDark;
    private final BooleanSetting showBountyHunter;
    private final HudConfig hud;

    public PlayerListConfig() {
        super("playerlist", source("Player List HUD"), ConfigCategory.RENDER,
                source("Shows player names, equipment, distance, and direction in a HUD."));
        subcategory("display", source("Display"));
        hud = hudConfig("player_list", HudAnchor.TOP_LEFT, 6, 6, true);
        for (PlayerListGroup group : PlayerListGroup.values()) {
            hudGroups.put(group, hudGroupConfig("player_list", group.id(),
                    TextCodes.strip(group.displayName()), hud, group.ordinal() * 40));
        }
        subsubcategory("enchantments", source("Enchantments"));
        enchantmentFormat = choiceSetting("enchantment_format", source("Enchantment format"),
                source("Controls how enchantment names and levels are shown."),
                PitEnchantmentFormat.BOLD_LEVELS, PitEnchantmentFormat.choices());
        showHeldItem = booleanSetting("show_held_item", source("Show held item enchantments"),
                source("Shows the held item's Pit enchantments."), false);
        showLeggings = booleanSetting("show_leggings", source("Show leggings enchantments"),
                source("Shows the leggings' Pit enchantments."), true);
        showGem = booleanSetting("show_gem", source("Show gem"),
                source("Shows a green diamond on gemmed items."), true);
        subsubcategory("player_information", source("Player information"));
        shortPlayerNames = booleanSetting("short_player_names", source("Remove bounty suffix"),
                source("Removes bounty suffixes from player names."), false);
        nickMarker = booleanSetting("nick_marker", source("Show nick marker"),
                source("Adds N to nicked players' names."), true);
        showDistance = booleanSetting("show_distance", source("Show player distance"),
                source("Shows the distance to each loaded player."), true);
        showDirection = booleanSetting("show_direction", source("Show player direction"),
                source("Shows an arrow pointing toward each loaded player."), true);
        showSpawn = booleanSetting("show_spawn", source("Show spawn marker"),
                source("Shows SPAWN instead of distance and direction for players in spawn."),
                true);
        hideSpawn = booleanSetting("hide_spawn", source("Hide players in spawn"),
                source("Hides players in spawn from all groups."), false);
        realNamesKey = keybindSetting("real_names_key", source("Real names key"),
                source("Hold to show nicked players' real names found by Denick."),
                Keyboard.KEY_TAB);
        subcategory("groups", source("Groups"));
        showGroupName = booleanSetting("show_group_name", source("Show group names"),
                source("Shows the name above each group."), true);
        showFriend = booleanSetting("show_friend", source("Show Friend group"),
                source("Shows friends."), true);
        showEnemy = booleanSetting("show_enemy", source("Show Enemy group"),
                source("Shows enemies."), true);
        showNick = booleanSetting("show_nick", source("Show Nick group"),
                source("Shows nicked players."), true);
        showRegularity = booleanSetting("show_regularity", source("Show Regularity group"),
                source("Shows players wearing Regularity leggings."), true);
        showDark = booleanSetting("show_dark", source("Show Dark group"),
                source("Shows players wearing Somber leggings."), true);
        showBountyHunter = booleanSetting("show_bounty_hunter", source("Show Bounty Hunter group"),
                source("Shows players wearing golden leggings."), false);
    }

    public boolean showHeldItem() {
        return showHeldItem.get();
    }

    public boolean showLeggings() {
        return showLeggings.get();
    }

    public int enchantmentFormat() {
        return enchantmentFormat.get();
    }

    public boolean showGem() {
        return showGem.get();
    }

    public boolean shortPlayerNames() {
        return shortPlayerNames.get();
    }

    public int realNamesKey() {
        return realNamesKey.get();
    }

    public boolean nickMarker() {
        return nickMarker.get();
    }

    public boolean showDistance() {
        return showDistance.get();
    }

    public boolean showDirection() {
        return showDirection.get();
    }

    public boolean showSpawn() {
        return showSpawn.get();
    }

    public boolean hideSpawn() {
        return hideSpawn.get();
    }

    public boolean showGroupName() {
        return showGroupName.get();
    }

    public HudConfig hud() {
        return hud;
    }

    public boolean showGroup(PlayerListGroup group) {
        switch (group) {
            case FRIEND:
                return showFriend.get();
            case ENEMY:
                return showEnemy.get();
            case NICK:
                return showNick.get();
            case REGULARITY:
                return showRegularity.get();
            case DARK:
                return showDark.get();
            case BOUNTY_HUNTER:
                return showBountyHunter.get();
            default:
                throw new IllegalStateException("Unknown player list group: " + group);
        }
    }
}
