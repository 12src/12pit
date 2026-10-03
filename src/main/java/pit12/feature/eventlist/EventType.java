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

enum EventType {
    BLOCKHEAD("blockhead", "Blockhead", 0xFFAA00, 300),
    ROBBERY("robbery", "Robbery", 0xFFAA00, 240),
    RAFFLE("raffle", "Raffle", 0xFFAA00, 300),
    CARE_PACKAGE("care_package", "Care Package", 0xFFAA00, 180),
    ALL_BOUNTY("all_bounty", "All bounty", 0xFFAA00, 180),
    PIZZA("pizza", "Pizza", 0xFF5555, 300),
    RAGE_PIT("rage_pit", "Rage Pit", 0xFF5555, 240),
    BEAST("beast", "Beast", 0x55FF55, 300),
    KOTL("kotl", "KOTL", 0x55FF55, 180),
    SPIRE("spire", "Spire", 0xAA00AA, 300),
    TEAM_DEATHMATCH("team_deathmatch", "Team Deathmatch", 0xAA00AA, 300),
    DRAGON_EGG("dragon_egg", "Dragon Egg", 0xAA00AA, 180),
    QUICK_MATHS("quick_maths", "Quick Maths", 0xAA00AA, 120),
    SQUADS("squads", "Squads", 0x55FFFF, 300),
    KOTH("koth", "KOTH", 0x55FFFF, 240),
    DOUBLE_REWARDS("2x_rewards", "2x Rewards", 0x00AA00, 240),
    GIANT_CAKE("giant_cake", "Giant Cake", 0xFF55FF, 120),
    AUCTION("auction", "Auction", 0xFFFF55, 180);

    final String id;
    final String displayName;
    final int color;
    final int durationSeconds;

    EventType(String id, String displayName, int color, int durationSeconds) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.durationSeconds = durationSeconds;
    }

    static EventType fromName(String name) {
        for (EventType type : values()) {
            if (type.displayName.equals(name)) {
                return type;
            }
        }
        return null;
    }
}
