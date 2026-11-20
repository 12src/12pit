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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public final class PlayerAudienceTest {
    private final UUID ownId = new UUID(0, 1);
    private final UUID remoteId = new UUID(0, 2);

    @Test
    public void ownPlayerAlwaysResolvesToOwnGroupEvenWhileLoading() {
        int group = PlayerAudience.groupOf(ownId, ownId,
                lookup(RelationReadiness.LOADING, Relation.NONE));
        assertEquals(PlayerAudience.OWN, group);
        assertTrue(PlayerAudience.matches(PlayerAudience.OWN, group));
        assertFalse(PlayerAudience.matches(PlayerAudience.REMOTE, group));
    }

    @Test
    public void unresolvedRemotePlayerMatchesOnlyWhenAllRemoteGroupsAreSelected() {
        int group = PlayerAudience.groupOf(remoteId, ownId,
                lookup(RelationReadiness.LOADING, Relation.FRIEND));
        assertEquals(PlayerAudience.REMOTE, group);
        assertFalse(PlayerAudience.matches(PlayerAudience.FRIEND, group));
        assertFalse(PlayerAudience.matches(PlayerAudience.ENEMY | PlayerAudience.OTHER, group));
        assertTrue(PlayerAudience.matches(PlayerAudience.REMOTE, group));
    }

    @Test
    public void readyRelationsResolveEachRemoteGroup() {
        int friend = PlayerAudience.groupOf(remoteId, ownId,
                lookup(RelationReadiness.READY, Relation.FRIEND));
        int enemy = PlayerAudience.groupOf(remoteId, ownId,
                lookup(RelationReadiness.READY, Relation.ENEMY));
        int other = PlayerAudience.groupOf(remoteId, ownId,
                lookup(RelationReadiness.READY, Relation.NONE));
        assertTrue(
                PlayerAudience.matches(PlayerAudience.groups(false, true, false, false), friend));
        assertTrue(PlayerAudience.matches(PlayerAudience.groups(false, false, true, false), enemy));
        assertTrue(PlayerAudience.matches(PlayerAudience.groups(false, false, false, true), other));
    }

    private static RelationLookup lookup(RelationReadiness readiness, Relation relation) {
        return new RelationLookup() {
            @Override
            public RelationReadiness readiness() {
                return readiness;
            }

            @Override
            public Relation relationOf(UUID playerId) {
                return relation;
            }

            @Override
            public List<RelationEntry> entries(Relation relation) {
                return Collections.emptyList();
            }

            @Override
            public void addListener(RelationListener listener) {}

            @Override
            public void removeListener(RelationListener listener) {}
        };
    }
}
