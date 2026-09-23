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

import java.util.UUID;

public final class PlayerListEntry {
    private final UUID playerId;
    private final int entityId;
    private final String name;
    private final PlayerListGroup group;
    private final String leggingsText;
    private final String heldItemText;
    private final float distance;
    private final String distanceText;
    private final float direction;
    private final boolean spawn;
    private final boolean distanceKnown;
    private final boolean directionKnown;

    PlayerListEntry(UUID playerId, int entityId, String name, PlayerListGroup group,
            String leggingsText, String heldItemText, float distance, float direction,
            boolean distanceKnown, boolean directionKnown, boolean spawn) {
        this.playerId = playerId;
        this.entityId = entityId;
        this.name = name;
        this.group = group;
        this.leggingsText = leggingsText;
        this.heldItemText = heldItemText;
        this.distance = distance;
        this.distanceText = spawn ? "SPAWN" : distanceKnown ? Math.round(distance) + "m" : ">MAX";
        this.direction = direction;
        this.spawn = spawn;
        this.distanceKnown = distanceKnown;
        this.directionKnown = directionKnown;
    }

    public UUID playerId() {
        return playerId;
    }

    int entityId() {
        return entityId;
    }

    public String name() {
        return name;
    }

    public PlayerListGroup group() {
        return group;
    }

    public String leggingsText() {
        return leggingsText;
    }

    public String heldItemText() {
        return heldItemText;
    }

    public float distance() {
        return distance;
    }

    public String distanceText() {
        return distanceText;
    }

    public float direction() {
        return direction;
    }

    public boolean spawn() {
        return spawn;
    }

    public boolean distanceKnown() {
        return distanceKnown;
    }

    public boolean directionKnown() {
        return directionKnown;
    }
}
