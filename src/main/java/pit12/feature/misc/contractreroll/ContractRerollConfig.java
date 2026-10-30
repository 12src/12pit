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
package pit12.feature.contractreroll;

import static pit12.runtime.languages.Languages.source;

import org.lwjgl.input.Keyboard;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ChoiceSetting.Choice;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.KeybindSetting;

public final class ContractRerollConfig extends FeatureConfig {
    final KeybindSetting key;
    final BooleanSetting requireViles;
    final ChoiceSetting kills;
    final ChoiceSetting noArmor;
    final ChoiceSetting fist;
    final ChoiceSetting chain;
    final ChoiceSetting gold;
    final ChoiceSetting bow;
    final ChoiceSetting noPerks;
    final ChoiceSetting apples;

    public ContractRerollConfig() {
        super("contractreroll", source("Contract Reroll"), ConfigCategory.MISC,
                source("Rerolls contracts until a task matches your filters."), false);
        key = keybindSetting("key", source("Contract reroll key"),
                source("Starts contract rerolling. Press again to stop."), Keyboard.KEY_NONE);
        requireViles = booleanSetting("require_viles", source("Require bonus viles"),
                source("Requires a Chunk of Vile bonus as well as a matching task."), false);
        subsubcategory("tasks", source("Contract filters"));
        kills = choiceSetting("kills", source("Kills"),
                source("Accepts tasks requiring at most the selected number of kills."), 0,
                new Choice(0, source("Off")), new Choice(15, source("At most 15")),
                new Choice(20, source("At most 20")));
        noArmor = choiceSetting("no_armor", source("Kills without armor"), source(
                "Accepts tasks requiring at most the selected number of kills without armor."), 0,
                new Choice(0, source("Off")), new Choice(3, source("At most 3")),
                new Choice(4, source("At most 4")));
        fist = choiceSetting("fist", source("Fist kills"), source(
                "Accepts tasks requiring at most the selected number of fist kills in the small sea lantern circle."),
                0, new Choice(0, source("Off")), new Choice(1, source("At most 1")),
                new Choice(2, source("At most 2")));
        chain = choiceSetting("chain", source("Multikills"),
                source("Accepts Double, Triple, and Quadra kill tasks up to the selected limit."),
                0, new Choice(0, source("Off")), new Choice(3, source("Up to Triple")),
                new Choice(4, source("Up to Quadra")));
        gold = choiceSetting("gold", source("Ingots"),
                source("Accepts tasks requiring at most the selected number of ingots."), 0,
                new Choice(0, source("Off")), new Choice(20, source("At most 20")),
                new Choice(25, source("At most 25")), new Choice(30, source("At most 30")));
        bow = choiceSetting("bow", source("Arrow hits"),
                source("Accepts tasks requiring at most the selected number of arrow hits."), 0,
                new Choice(0, source("Off")), new Choice(100, source("At most 100")),
                new Choice(125, source("At most 125")), new Choice(150, source("At most 150")));
        noPerks = choiceSetting("no_perks", source("Kills without perks"), source(
                "Accepts tasks requiring at most the selected number of kills without perks."), 0,
                new Choice(0, source("Off")), new Choice(8, source("At most 8")),
                new Choice(10, source("At most 10")));
        apples = choiceSetting("apples", source("Golden apples"), source(
                "Accepts tasks requiring at most the selected number of golden apples eaten."), 0,
                new Choice(0, source("Off")), new Choice(12, source("At most 12")),
                new Choice(16, source("At most 16")), new Choice(18, source("At most 18")));
    }
}
