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
package pit12.feature.playeresp;

import static pit12.runtime.languages.Languages.source;

import pit12.feature.relation.api.Relation;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ColorSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class PlayerEspConfig extends FeatureConfig {
    private final BooleanSetting showFriend;
    private final BooleanSetting showEnemy;
    private final BooleanSetting showOther;
    private final ColorSetting friendColor;
    private final ColorSetting enemyColor;
    private final ColorSetting otherColor;
    private final BooleanSetting distanceFade;
    private final IntegerSetting fadeNearDistance;
    private final IntegerSetting fadeFarDistance;

    public PlayerEspConfig() {
        super("playeresp", source("Player ESP"),
                new ConfigCategory("render", source("Render"), 100),
                source("Shows colored boxes around loaded players through blocks."), true, false);
        subsubcategory("friends", source("Friends"));
        showFriend = booleanSetting("show_friend", source("Show friends"), source("Marks friends."),
                true);
        friendColor = colorPickerSetting("friend_color", source("Friend color"), "", 0xC000FF00);
        subsubcategory("enemies", source("Enemies"));
        showEnemy = booleanSetting("show_enemy", source("Show enemies"), source("Marks enemies."),
                true);
        enemyColor = colorPickerSetting("enemy_color", source("Enemy color"), "", 0xC0FF0000);
        subsubcategory("other_players", source("Other players"));
        showOther = booleanSetting("show_other", source("Show other players"),
                source("Marks players outside the friend and enemy lists."), false);
        otherColor =
                colorPickerSetting("other_color", source("Other player color"), "", 0xC0FFFFFF);
        subsubcategory("distance_fade", source("Distance fade"));
        distanceFade = booleanSetting("distance_fade", source("Fade nearby players"),
                source("Makes boxes fade out as players get closer."), true);
        fadeNearDistance = integerSliderSetting("fade_near_distance", source("Hidden distance"),
                source("Hides boxes at or below the smaller distance."), 5, 0, 20, 1);
        fadeFarDistance = integerSliderSetting("fade_far_distance", source("Full color distance"),
                source("Uses full color at or above the larger distance."), 10, 0, 20, 1);
    }

    public boolean hasTargets() {
        return showFriend.get() || showEnemy.get() || showOther.get();
    }

    public boolean shows(Relation relation) {
        switch (relation) {
            case FRIEND:
                return showFriend.get();
            case ENEMY:
                return showEnemy.get();
            default:
                return showOther.get();
        }
    }

    public int friendColor() {
        return friendColor.get();
    }

    public int enemyColor() {
        return enemyColor.get();
    }

    public int otherColor() {
        return otherColor.get();
    }

    public boolean distanceFade() {
        return distanceFade.get();
    }

    public int fadeNearDistance() {
        return fadeNearDistance.get();
    }

    public int fadeFarDistance() {
        return fadeFarDistance.get();
    }
}
