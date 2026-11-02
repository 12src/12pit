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
package pit12.feature.render.playeresp;

import static pit12.runtime.languages.Languages.source;

import pit12.feature.client.relation.api.Relation;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ColorSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.player.PlayerEquipmentSnapshot;

public final class PlayerEspConfig extends FeatureConfig {
    private final BooleanSetting showFriend;
    private final BooleanSetting showEnemy;
    private final BooleanSetting showOther;
    private final ColorSetting friendColor;
    private final ColorSetting enemyColor;
    private final ColorSetting otherColor;
    private final BooleanSetting showVenom;
    private final BooleanSetting showBountyHunter;
    private final ColorSetting venomColor;
    private final ColorSetting bountyHunterColor;
    private final ChoiceSetting priority;
    private final BooleanSetting distanceFade;
    private final IntegerSetting fadeNearDistance;
    private final IntegerSetting fadeFarDistance;

    public PlayerEspConfig() {
        super("playeresp", source("Player ESP"), ConfigCategory.RENDER,
                source("Shows colored boxes around loaded players through blocks."));
        subcategory("general", source("General"));
        priority = choiceSetting("priority", source("Color priority"), source(
                "Chooses which color to use when conflicting. Mixed averages their colors and opacity."),
                0, new ChoiceSetting.Choice(0, source("Relations")),
                new ChoiceSetting.Choice(1, source("Equipment")),
                new ChoiceSetting.Choice(2, source("Mixed")));
        subsubcategory("distance_fade", source("Distance fade"));
        distanceFade = booleanSetting("distance_fade", source("Fade nearby players"),
                source("Makes boxes fade out as players get closer."), true);
        fadeNearDistance =
                integerSliderSetting("fade_near_distance", source("Hidden distance (blocks)"),
                        source("Hides boxes at or below the smaller distance."), 5, 0, 20, 1);
        fadeFarDistance =
                integerSliderSetting("fade_far_distance", source("Full color distance (blocks)"),
                        source("Uses full color at or above the larger distance."), 10, 0, 20, 1);
        subcategory("relations", source("Relations"));
        subsubcategory("friends", source("Friends"));
        showFriend = booleanSetting("show_friend", source("Show friends"), source("Marks friends."),
                true);
        friendColor = colorPickerSetting("friend_color", source("Friend color"),
                source("Color and opacity of boxes around friends."), 0xC000FF00);
        subsubcategory("enemies", source("Enemies"));
        showEnemy = booleanSetting("show_enemy", source("Show enemies"), source("Marks enemies."),
                true);
        enemyColor = colorPickerSetting("enemy_color", source("Enemy color"),
                source("Color and opacity of boxes around enemies."), 0xC0FF0000);
        subsubcategory("other_players", source("Other players"));
        showOther = booleanSetting("show_other", source("Show other players"),
                source("Marks players outside the friend and enemy lists."), false);
        otherColor = colorPickerSetting("other_color", source("Other player color"),
                source("Color and opacity of boxes around other players."), 0xC0FFFFFF);
        subcategory("equipment", source("Equipment"));
        subsubcategory("venom", "Venom");
        showVenom = booleanSetting("show_venom", source("Show Venom"),
                source("Marks players wearing Venom leggings."), true);
        venomColor = colorPickerSetting("venom_color", source("Venom color"),
                source("Color and opacity of boxes around players wearing Venom leggings."),
                0xC0AA00AA);
        subsubcategory("bounty_hunter", "Bounty Hunter");
        showBountyHunter = booleanSetting("show_bounty_hunter", source("Show Bounty Hunter"),
                source("Marks players wearing golden leggings."), false);
        bountyHunterColor = colorPickerSetting("bounty_hunter_color", source("Bounty Hunter color"),
                source("Color and opacity of boxes around players wearing golden leggings."),
                0xC0FFAA00);
    }

    public boolean hasTargets() {
        return showFriend.get() || showEnemy.get() || showOther.get() || hasEquipmentTargets();
    }

    boolean hasEquipmentTargets() {
        return showVenom.get() || showBountyHunter.get();
    }

    int color(Relation relation, PlayerEquipmentSnapshot equipment) {
        int relationColor = 0;
        if (relation != null) {
            switch (relation) {
                case FRIEND:
                    relationColor = showFriend.get() ? friendColor.get() : 0;
                    break;
                case ENEMY:
                    relationColor = showEnemy.get() ? enemyColor.get() : 0;
                    break;
                default:
                    relationColor = showOther.get() ? otherColor.get() : 0;
            }
        }
        int equipmentColor = 0;
        if (equipment != null) {
            if (showVenom.get()
                    && equipment.leggingsEnchantments().contains(PitEnchantment.Combo_Venom)) {
                equipmentColor = venomColor.get();
            } else if (showBountyHunter.get() && equipment.hasGoldenLeggings()) {
                equipmentColor = bountyHunterColor.get();
            }
        }
        if ((relationColor >>> 24) == 0) {
            return equipmentColor;
        }
        if ((equipmentColor >>> 24) == 0) {
            return relationColor;
        }
        switch (priority.get()) {
            case 0:
                return relationColor;
            case 1:
                return equipmentColor;
            default:
                int alpha = ((relationColor >>> 24) + (equipmentColor >>> 24)) / 2;
                int red = ((relationColor >> 16 & 0xFF) + (equipmentColor >> 16 & 0xFF)) / 2;
                int green = ((relationColor >> 8 & 0xFF) + (equipmentColor >> 8 & 0xFF)) / 2;
                int blue = ((relationColor & 0xFF) + (equipmentColor & 0xFF)) / 2;
                return alpha << 24 | red << 16 | green << 8 | blue;
        }
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
