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

import java.util.List;
import java.util.UUID;

/** Use this API on the client thread. Only trust NONE once readiness is READY. */
public interface RelationLookup {
    RelationReadiness readiness();

    Relation relationOf(UUID playerId);

    default boolean matches(UUID playerId, UUID ownPlayerId, boolean own, boolean friends,
            boolean enemies, boolean others) {
        if (playerId.equals(ownPlayerId))
            return own;
        if (friends == enemies && enemies == others)
            return friends;
        if (readiness() != RelationReadiness.READY)
            return false;
        switch (relationOf(playerId)) {
            case FRIEND:
                return friends;
            case ENEMY:
                return enemies;
            default:
                return others;
        }
    }

    List<RelationEntry> entries(Relation relation);

    /** Listeners receive future changes only. */
    void addListener(RelationListener listener);

    void removeListener(RelationListener listener);
}
