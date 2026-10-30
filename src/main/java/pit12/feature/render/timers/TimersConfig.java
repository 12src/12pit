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
package pit12.feature.timers;

import static pit12.runtime.languages.Languages.source;

import java.util.EnumMap;
import java.util.Map;
import pit12.feature.timers.TimersHud.TimerType;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ColorSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.DoubleSetting;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.HudAnchor;
import pit12.runtime.config.HudConfig;
import pit12.runtime.config.HudGroupConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.shared.text.TextCodes;

public final class TimersConfig extends FeatureConfig {
    final Map<TimerType, HudGroupConfig> hudGroups = new EnumMap<>(TimerType.class);
    public final BooleanSetting pinEnabled;
    public final BooleanSetting pinOwn;
    public final BooleanSetting pinFriends;
    public final BooleanSetting pinEnemies;
    public final BooleanSetting pinOthers;
    public final IntegerSetting pinHitWindow;
    public final BooleanSetting venomEnabled;
    public final BooleanSetting venomSelf;
    public final BooleanSetting venomTargets;
    public final BooleanSetting venomOwn;
    public final BooleanSetting venomFriends;
    public final BooleanSetting venomEnemies;
    public final BooleanSetting venomOthers;
    public final IntegerSetting venomHitWindow;
    public final DoubleSetting venomReach;
    public final DoubleSetting venomHitboxMargin;
    public final BooleanSetting jumpBoostEnabled;
    public final BooleanSetting jumpBoostOwn;
    public final BooleanSetting jumpBoostFriends;
    public final BooleanSetting jumpBoostEnemies;
    public final BooleanSetting jumpBoostOthers;
    public final BooleanSetting jumpBoostSoundRequired;
    public final BooleanSetting jumpBoostResetOnLowJump;
    public final IntegerSetting jumpBoostGrace;
    public final BooleanSetting telebowEnabled;
    public final BooleanSetting telebowOwn;
    public final BooleanSetting telebowFriends;
    public final BooleanSetting telebowEnemies;
    public final BooleanSetting telebowOthers;
    public final BooleanSetting showGroupNames;
    public final ColorSetting ownColor;
    public final ColorSetting friendColor;
    public final ColorSetting enemyColor;
    public final ColorSetting otherColor;
    public final HudConfig hud;

    public TimersConfig() {
        super("debufftimers", source("Timers HUD"), ConfigCategory.RENDER, source(
                "Shows estimated Pin, Venom, and Jump Boost durations and Telebow cooldowns."));
        hud = hudConfig("debuff_timers", HudAnchor.TOP_RIGHT, 6, 6, true, HudGroup.SUBCATEGORY,
                "HUD");
        for (TimerType type : TimerType.values()) {
            hudGroups.put(type, hudGroupConfig("debuff_timers", type.id,
                    TextCodes.strip(type.title), hud, type.ordinal() * 40));
        }
        subcategory("display", source("Display"));
        showGroupNames = booleanSetting("show_group_names", source("Show group names"),
                source("Shows the name above each timer group."), true);
        ownColor = colorPickerSetting("own_color", source("Own color"),
                source("Color and opacity of your countdowns."), 0xFF55FFFF);
        friendColor = colorPickerSetting("friend_color", source("Friend color"),
                source("Color and opacity of friends' countdowns."), 0xFF55FF55);
        enemyColor = colorPickerSetting("enemy_color", source("Enemy color"),
                source("Color and opacity of enemies' countdowns."), 0xFFFF5555);
        otherColor = colorPickerSetting("other_color", source("Other player color"),
                source("Color and opacity of other players' countdowns."), 0xFFFFFFFF);
        subcategory("debuffs", source("Debuffs"));
        subsubcategory("pin", "Pin");
        pinEnabled = booleanSetting("pin_enabled", source("Enabled"),
                source("Shows estimated Pin durations."), true);
        pinOwn = booleanSetting("own", source("Own"), source("Shows your Pin timer."), true);
        pinFriends = booleanSetting("friends", source("Friends"),
                source("Shows friends' Pin timers."), true);
        pinEnemies = booleanSetting("enemies", source("Enemies"),
                source("Shows enemies' Pin timers."), true);
        pinOthers = booleanSetting("others", source("Others"),
                source("Shows other players' Pin timers."), true);
        pinHitWindow = integerSliderSetting("pin_hit_window_ms", source("Prediction margin (ms)"),
                source("Time allowed for delayed damage to count as a Pin hit."), 250, 50, 1000,
                25);
        subsubcategory("venom", "Venom");
        venomEnabled = booleanSetting("venom_enabled", source("Enabled"),
                source("Shows estimated Venom durations."), true);
        venomSelf = booleanSetting("venom_self", source("Track wearers"),
                source("Shows the wearer's estimated Venom duration."), true);
        venomTargets = booleanSetting("venom_targets", source("Track targets"),
                source("Shows the target's estimated Venom duration."), true);
        venomOwn =
                booleanSetting("venom_own", source("Own"), source("Shows your Venom timer."), true);
        venomFriends = booleanSetting("venom_friends", source("Friends"),
                source("Shows friends' Venom timers."), true);
        venomEnemies = booleanSetting("venom_enemies", source("Enemies"),
                source("Shows enemies' Venom timers."), true);
        venomOthers = booleanSetting("venom_others", source("Others"),
                source("Shows other players' Venom timers."), true);
        venomHitWindow =
                integerSliderSetting("venom_hit_window_ms", source("Prediction margin (ms)"),
                        source("Time allowed for delayed damage to count as a Venom hit."), 250, 50,
                        1500, 25);
        venomReach = doubleSliderSetting("venom_reach_blocks", source("Maximum reach (blocks)"),
                source("Maximum distance at which hits count toward Venom estimates."), 4.5, 2, 6,
                0.1);
        venomHitboxMargin = doubleSliderSetting("venom_hitbox_margin_blocks",
                source("Hitbox margin (blocks)"),
                source("Counts hits up to this far outside player hitboxes toward Venom timers."),
                0.4, 0, 1, 0.05);
        subcategory("buffs", source("Buffs"));
        subsubcategory("jump_boost", "Jump Boost");
        jumpBoostEnabled = booleanSetting("jump_boost_enabled", source("Enabled"),
                source("Shows estimated Jump Boost IV durations."), true);
        jumpBoostOwn = booleanSetting("jump_boost_own", source("Own"),
                source("Shows your Jump Boost timer."), true);
        jumpBoostFriends = booleanSetting("jump_boost_friends", source("Friends"),
                source("Shows friends' Jump Boost timers."), true);
        jumpBoostEnemies = booleanSetting("jump_boost_enemies", source("Enemies"),
                source("Shows enemies' Jump Boost timers."), true);
        jumpBoostOthers = booleanSetting("jump_boost_others", source("Others"),
                source("Shows other players' Jump Boost timers."), true);
        jumpBoostSoundRequired =
                booleanSetting("jump_boost_sound_required", source("Sound required"),
                        source("Requires a drinking sound to start a Jump Boost timer."), false);
        jumpBoostResetOnLowJump = booleanSetting("jump_boost_reset_on_low_jump",
                source("Reset on low jump"),
                source("Clears the timer when an unobstructed jump is too low for Jump Boost IV."),
                true);
        jumpBoostGrace = integerSliderSetting("jump_boost_grace_ms",
                source("Prediction margin (ms)"),
                source("Time after a Jump Boost timer starts before low jumps can clear it."), 250,
                0, 1000, 25);
        subcategory("cooldowns", source("Cooldowns"));
        subsubcategory("telebow", "Telebow");
        telebowEnabled = booleanSetting("telebow_enabled", source("Enabled"), source(
                "Shows estimated Telebow cooldowns. Requires Telebow Prediction to be enabled."),
                true);
        telebowOwn = booleanSetting("telebow_own", source("Own"),
                source("Shows your Telebow cooldown."), true);
        telebowFriends = booleanSetting("telebow_friends", source("Friends"),
                source("Shows friends' Telebow cooldowns."), true);
        telebowEnemies = booleanSetting("telebow_enemies", source("Enemies"),
                source("Shows enemies' Telebow cooldowns."), true);
        telebowOthers = booleanSetting("telebow_others", source("Others"),
                source("Shows other players' Telebow cooldowns."), true);
    }
}
