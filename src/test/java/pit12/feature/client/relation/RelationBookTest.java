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
package pit12.feature.client.relation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static pit12.feature.client.relation.api.Relation.ENEMY;
import static pit12.feature.client.relation.api.Relation.FRIEND;
import static pit12.feature.client.relation.api.Relation.NONE;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import pit12.feature.client.relation.api.RelationEntry;
import pit12.runtime.languages.Languages;
import pit12.shared.concurrent.ClientThread;

public final class RelationBookTest {
    private final RelationBook book = new RelationBook(new Languages(ClientThread.current()));

    @Test
    public void onlinePlayerBindsUuidImmediatelyAndSwitchesGroupsWithoutDuplicates() {
        UUID id = new UUID(0, 1);
        Map<UUID, String> online = Collections.singletonMap(id, "Foo");
        assertTrue(book.change(FRIEND, "add", "foo", online).succeeded);
        assertEquals(FRIEND, book.relationOf(id));
        assertNull(book.pending("foo"));
        assertTrue(book.change(ENEMY, "add", "foo", online).succeeded);
        assertEquals(ENEMY, book.relationOf(id));
        assertTrue(book.entries(FRIEND).isEmpty());
        assertEquals(1, book.entries(ENEMY).size());
        assertTrue(book.change(ENEMY, "toggle", "foo", online).succeeded);
        assertEquals(NONE, book.relationOf(id));
    }

    @Test
    public void rejectsOnlinePlayerWhenNameAlreadyBelongsToDifferentSavedUuid() {
        UUID savedId = new UUID(0, 1);
        UUID onlineId = new UUID(0, 2);
        book.replace(Collections.singletonList(new RelationEntry(savedId, "Foo", FRIEND)));
        RelationBook.Change result =
                book.change(ENEMY, "add", "foo", Collections.singletonMap(onlineId, "Foo"));
        assertFalse(result.succeeded);
        assertEquals(FRIEND, book.relationOf(savedId));
        assertEquals(NONE, book.relationOf(onlineId));
    }

    @Test
    public void observePromotesPendingEntryAndUpdatesRenamedKnownPlayer() {
        UUID id = new UUID(0, 1);
        book.change(FRIEND, "add", "foo", Collections.emptyMap());
        assertNotNull(book.pending("FOO"));
        assertTrue(book.observe(id, "Foo"));
        assertNull(book.pending("foo"));
        assertEquals(FRIEND, book.relationOf(id));
        assertEquals("Foo", book.entries(FRIEND).get(0).name());
        assertFalse(book.observe(id, "Foo"));
        assertTrue(book.observe(id, "Bar"));
        assertEquals("Bar", book.entries(FRIEND).get(0).name());
    }

    @Test
    public void bindRejectsReplacedPendingEntry() {
        UUID id = new UUID(0, 1);
        book.change(FRIEND, "add", "foo", Collections.emptyMap());
        RelationEntry stale = book.pending("foo");
        book.change(ENEMY, "add", "foo", Collections.emptyMap());
        assertFalse(book.bind(stale, id, "Foo"));
        assertEquals(NONE, book.relationOf(id));
        assertEquals(ENEMY, book.pending("foo").relation());
    }
}
