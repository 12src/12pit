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
package pit12.feature.eventlist;

import static pit12.runtime.languages.Languages.source;

enum EventType {
    BLOCKHEAD("blockhead", source("Blockhead"), 0xFFAA00, 300, true),
    ROBBERY("robbery", source("Robbery"), 0xFFAA00, 240, true),
    RAFFLE("raffle", source("Raffle"), 0xFFAA00, 300, true),
    CARE_PACKAGE("care_package", source("Care Package"), 0xFFAA00, 180, false),
    ALL_BOUNTY("all_bounty", source("All bounty"), 0xFFAA00, 180, false),
    PIZZA("pizza", source("Pizza"), 0xFF5555, 300, true),
    RAGE_PIT("rage_pit", source("Rage Pit"), 0xFF5555, 240, true),
    BEAST("beast", source("Beast"), 0x55FF55, 300, true),
    KOTL("kotl", source("KOTL"), 0x55FF55, 180, false),
    SPIRE("spire", source("Spire"), 0xAA00AA, 300, true),
    TEAM_DEATHMATCH("team_deathmatch", source("Team Deathmatch"), 0xAA00AA, 300, true),
    DRAGON_EGG("dragon_egg", source("Dragon Egg"), 0xAA00AA, 180, false),
    QUICK_MATHS("quick_maths", source("Quick Maths"), 0xAA00AA, 120, false),
    SQUADS("squads", source("Squads"), 0x55FFFF, 300, true),
    KOTH("koth", source("KOTH"), 0x55FFFF, 240, false),
    DOUBLE_REWARDS("2x_rewards", source("2x Rewards"), 0x00AA00, 240, false),
    GIANT_CAKE("giant_cake", source("Giant Cake"), 0xFF55FF, 120, false),
    AUCTION("auction", source("Auction"), 0xFFFF55, 180, false);

    final String id;
    final String displayName;
    final int color;
    final int durationSeconds;
    final boolean major;

    EventType(String id, String displayName, int color, int durationSeconds, boolean major) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.durationSeconds = durationSeconds;
        this.major = major;
    }

    static EventType fromName(String name) {
        for (EventType type : values()) {
            if (type.id.equalsIgnoreCase(name) || type.displayName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
