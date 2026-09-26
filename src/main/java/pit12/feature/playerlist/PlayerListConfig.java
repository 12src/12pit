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
    private final BooleanSetting showDistance;
    private final BooleanSetting showDirection;
    private final BooleanSetting showSpawn;
    private final BooleanSetting showGroupName;
    private final BooleanSetting showFriend;
    private final BooleanSetting showEnemy;
    private final BooleanSetting showRegularity;
    private final BooleanSetting showDark;
    private final BooleanSetting showBountyHunter;
    private final BooleanSetting useVanillaFont;
    private final HudConfig hud;

    public PlayerListConfig() {
        super("playerlist", "Player List", new ConfigCategory("render", "Render", 100),
                "Shows loaded player equipment and direction in a compact HUD.");
        subcategory("display", "Display");
        hud = hudConfig("player_list", "Player List", HudAnchor.TOP_LEFT, 6, 6, true);
        useVanillaFont = booleanSetting("use_vanilla_font", "Use vanilla font",
                "Uses Minecraft's font renderer for player list text.", true);
        enchantmentFormat = choiceSetting("enchantment_format", "Enchantment format",
                "Controls how enchantment names and levels are shown.",
                ENCHANTMENT_FORMAT_BOLD_LEVELS,
                new ChoiceSetting.Choice(ENCHANTMENT_FORMAT_BOLD_LEVELS,
                        "§4§lREG §f§l3§7 / §6§lABS §f§l2"),
                new ChoiceSetting.Choice(ENCHANTMENT_FORMAT_PLAIN_LEVELS,
                        "§4REG §f3§7 / §6ABS §f2"),
                new ChoiceSetting.Choice(ENCHANTMENT_FORMAT_HIDE_LEVEL_THREE,
                        "§4§lREG§7 / §6§lABS §f§l2"),
                new ChoiceSetting.Choice(ENCHANTMENT_FORMAT_NAMES_ONLY, "§4§lREG§7 / §6§lABS"));
        subcategory("enchantments", "Enchantments");
        showHeldItem = booleanSetting("show_held_item", "Show held item enchantments",
                "Shows the held item's Pit enchantments.", false);
        showLeggings = booleanSetting("show_leggings", "Show leggings enchantments",
                "Shows the leggings' Pit enchantments.", true);
        subcategory("groups", "Groups");
        showGroupName = booleanSetting("show_group_name", "Show group names",
                "Shows the name above each equipment group.", true);
        showFriend = booleanSetting("show_friend", "Show Friend group",
                "Shows friends in the player list.", true);
        showEnemy = booleanSetting("show_enemy", "Show Enemy group",
                "Shows enemies in the player list.", true);
        showRegularity = booleanSetting("show_regularity", "Show Regularity group",
                "Shows the Regularity equipment group.", true);
        showDark = booleanSetting("show_dark", "Show Dark group", "Shows the Dark equipment group.",
                true);
        showBountyHunter = booleanSetting("show_bounty_hunter", "Show Bounty Hunter group",
                "Shows the Bounty Hunter equipment group.", false);
        subcategory("player_information", "Player information");
        showDistance = booleanSetting("show_distance", "Show player distance",
                "Shows the distance to each loaded player.", true);
        showDirection = booleanSetting("show_direction", "Show player direction",
                "Shows the continuous direction to each loaded player.", true);
        showSpawn = booleanSetting("show_spawn", "Show spawn marker",
                "Shows SPAWN instead of distance and direction for players in spawn.", true);
    }

    public boolean showHeldItem() {
        return showHeldItem.get().booleanValue();
    }

    public boolean showLeggings() {
        return showLeggings.get().booleanValue();
    }

    public int enchantmentFormat() {
        return enchantmentFormat.get().intValue();
    }

    public boolean showDistance() {
        return showDistance.get().booleanValue();
    }

    public boolean showDirection() {
        return showDirection.get().booleanValue();
    }

    public boolean showSpawn() {
        return showSpawn.get().booleanValue();
    }

    public boolean showGroupName() {
        return showGroupName.get().booleanValue();
    }

    public boolean showFriend() {
        return showFriend.get().booleanValue();
    }

    public boolean showEnemy() {
        return showEnemy.get().booleanValue();
    }

    public boolean showRegularity() {
        return showRegularity.get().booleanValue();
    }

    public boolean showDark() {
        return showDark.get().booleanValue();
    }

    public boolean showBountyHunter() {
        return showBountyHunter.get().booleanValue();
    }

    public boolean useVanillaFont() {
        return useVanillaFont.get().booleanValue();
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
                return false;
        }
    }
}
