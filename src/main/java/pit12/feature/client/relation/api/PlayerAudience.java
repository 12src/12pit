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
package pit12.feature.client.relation.api;

import java.util.UUID;

public final class PlayerAudience {
    public static final int OWN = 1;
    public static final int FRIEND = 2;
    public static final int ENEMY = 4;
    public static final int OTHER = 8;
    public static final int REMOTE = FRIEND | ENEMY | OTHER;
    public static final int ALL = OWN | REMOTE;

    private PlayerAudience() {}

    public static int groupOf(UUID playerId, UUID ownId, RelationLookup relations) {
        if (playerId.equals(ownId))
            return OWN;
        if (relations.readiness() != RelationReadiness.READY)
            return REMOTE;
        Relation relation = relations.relationOf(playerId);
        return relation == Relation.FRIEND ? FRIEND : relation == Relation.ENEMY ? ENEMY : OTHER;
    }

    public static boolean matches(int audience, int group) {
        // An unresolved relation can belong to any remote group.
        return group != 0 && (audience & group) == group;
    }

    public static int groups(boolean own, boolean friends, boolean enemies, boolean others) {
        return (own ? OWN : 0) | (friends ? FRIEND : 0) | (enemies ? ENEMY : 0)
                | (others ? OTHER : 0);
    }
}
