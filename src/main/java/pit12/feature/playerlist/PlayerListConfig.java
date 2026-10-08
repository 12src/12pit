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

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.HudAnchor;
import pit12.runtime.config.HudConfig;

public final class PlayerListConfig extends FeatureConfig {
    static final int ENCHANTMENT_FORMAT_BOLD_LEVELS = 0;
    static final int ENCHANTMENT_FORMAT_PLAIN_LEVELS = 1;
    static final int ENCHANTMENT_FORMAT_HIDE_LEVEL_THREE = 2;
    static final int ENCHANTMENT_FORMAT_NAMES_ONLY = 3;
    private final BooleanSetting showHeldItem;
    private final BooleanSetting showLeggings;
    private final ChoiceSetting enchantmentFormat;
    private final BooleanSetting shortPlayerNames;
    private final BooleanSetting showDistance;
    private final BooleanSetting showDirection;
    private final BooleanSetting showSpawn;
    private final BooleanSetting showGroupName;
    private final BooleanSetting showFriend;
    private final BooleanSetting showEnemy;
    private final BooleanSetting showRegularity;
    private final BooleanSetting showDark;
    private final BooleanSetting showBountyHunter;
    private final HudConfig hud;

    public PlayerListConfig() {
        super("playerlist", source("Player List"),
                new ConfigCategory("render", source("Render"), 100),
                source("Shows loaded player equipment and direction in a compact HUD."));
        subcategory("display", source("Display"));
        hud = hudConfig("player_list", HudAnchor.TOP_LEFT, 6, 6, true);
        subsubcategory("enchantments", source("Enchantments"));
        enchantmentFormat = choiceSetting("enchantment_format", source("Enchantment format"),
                source("Controls how enchantment names and levels are shown."),
                ENCHANTMENT_FORMAT_BOLD_LEVELS,
                new ChoiceSetting.Choice(ENCHANTMENT_FORMAT_BOLD_LEVELS,
                        "§4§lREG §f§l3§7 / §6§lABS §f§l2"),
                new ChoiceSetting.Choice(ENCHANTMENT_FORMAT_PLAIN_LEVELS,
                        "§4REG §f3§7 / §6ABS §f2"),
                new ChoiceSetting.Choice(ENCHANTMENT_FORMAT_HIDE_LEVEL_THREE,
                        "§4§lREG§7 / §6§lABS §f§l2"),
                new ChoiceSetting.Choice(ENCHANTMENT_FORMAT_NAMES_ONLY, "§4§lREG§7 / §6§lABS"));
        showHeldItem = booleanSetting("show_held_item", source("Show held item enchantments"),
                source("Shows the held item's Pit enchantments."), false);
        showLeggings = booleanSetting("show_leggings", source("Show leggings enchantments"),
                source("Shows the leggings' Pit enchantments."), true);
        subsubcategory("player_information", source("Player information"));
        shortPlayerNames = booleanSetting("short_player_names", source("Short player names"),
                source("Shows only the level and player name, keeping their formatting."), false);
        showDistance = booleanSetting("show_distance", source("Show player distance"),
                source("Shows the distance to each loaded player."), true);
        showDirection = booleanSetting("show_direction", source("Show player direction"),
                source("Shows the continuous direction to each loaded player."), true);
        showSpawn = booleanSetting("show_spawn", source("Show spawn marker"),
                source("Shows SPAWN instead of distance and direction for players in spawn."),
                true);
        subcategory("groups", source("Groups"));
        showGroupName = booleanSetting("show_group_name", source("Show group names"),
                source("Shows the name above each equipment group."), true);
        showFriend = booleanSetting("show_friend", source("Show Friend group"),
                source("Shows friends in the player list."), true);
        showEnemy = booleanSetting("show_enemy", source("Show Enemy group"),
                source("Shows enemies in the player list."), true);
        showRegularity = booleanSetting("show_regularity", source("Show Regularity group"),
                source("Shows the Regularity equipment group."), true);
        showDark = booleanSetting("show_dark", source("Show Dark group"),
                source("Shows the Dark equipment group."), true);
        showBountyHunter = booleanSetting("show_bounty_hunter", source("Show Bounty Hunter group"),
                source("Shows the Bounty Hunter equipment group."), false);
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

    public boolean shortPlayerNames() {
        return shortPlayerNames.get();
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

    public boolean showGroupName() {
        return showGroupName.get();
    }

    public boolean showFriend() {
        return showFriend.get();
    }

    public boolean showEnemy() {
        return showEnemy.get();
    }

    public boolean showRegularity() {
        return showRegularity.get();
    }

    public boolean showDark() {
        return showDark.get();
    }

    public boolean showBountyHunter() {
        return showBountyHunter.get();
    }

    public HudConfig hud() {
        return hud;
    }

    public boolean showGroup(PlayerListGroup group) {
        switch (group) {
            case FRIEND:
                return showFriend();
            case ENEMY:
                return showEnemy();
            case REGULARITY:
                return showRegularity();
            case DARK:
                return showDark();
            case BOUNTY_HUNTER:
                return showBountyHunter();
            default:
                throw new IllegalStateException("Unknown player list group: " + group);
        }
    }
}
