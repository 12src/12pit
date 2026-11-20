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
package pit12.feature.client.relation.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static pit12.feature.client.relation.api.Relation.ENEMY;
import static pit12.feature.client.relation.api.Relation.FRIEND;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import pit12.feature.client.relation.api.RelationEntry;

public final class RelationStoreTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void missingFileLoadsEmptyList() throws IOException {
        Path path = folder.getRoot().toPath().resolve("relations.json");
        assertTrue(new RelationStore(path).load().isEmpty());
    }

    @Test
    public void roundTripsEntries() throws IOException {
        Path path = folder.getRoot().toPath().resolve("relations.json");
        RelationStore store = new RelationStore(path);
        UUID id = new UUID(0, 1);
        store.write(Arrays.asList(new RelationEntry(id, "Foo", FRIEND),
                new RelationEntry(null, "Bar", ENEMY)));
        List<RelationEntry> loaded = store.load();
        assertEquals(2, loaded.size());
        RelationEntry known = loaded.get(0);
        assertEquals(id, known.playerId());
        assertEquals("Foo", known.name());
        assertEquals(FRIEND, known.relation());
        RelationEntry pending = loaded.get(1);
        assertNull(pending.playerId());
        assertEquals("Bar", pending.name());
        assertEquals(ENEMY, pending.relation());
    }

    @Test
    public void rejectsUnsupportedSchemaVersion() throws IOException {
        Path path = folder.getRoot().toPath().resolve("relations.json");
        Files.write(path, "{\"schemaVersion\":2,\"entries\":[]}".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, new RelationStore(path)::load);
    }

    @Test
    public void rejectsDuplicateUuidOrCaseInsensitivePendingName() throws IOException {
        UUID id = new UUID(0, 1);
        Path duplicateUuid = folder.getRoot().toPath().resolve("dup-uuid.json");
        Files.write(duplicateUuid,
                ("{\"schemaVersion\":1,\"entries\":[" + "{\"uuid\":\"" + id
                        + "\",\"name\":\"Foo\",\"relation\":\"FRIEND\"}," + "{\"uuid\":\"" + id
                        + "\",\"name\":\"Bar\",\"relation\":\"ENEMY\"}]}")
                        .getBytes(StandardCharsets.UTF_8));
        Path duplicatePending = folder.getRoot().toPath().resolve("dup-name.json");
        Files.write(duplicatePending,
                ("{\"schemaVersion\":1,\"entries\":["
                        + "{\"name\":\"Foo\",\"relation\":\"FRIEND\"},"
                        + "{\"name\":\"foo\",\"relation\":\"ENEMY\"}]}")
                        .getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, new RelationStore(duplicateUuid)::load);
        assertThrows(IllegalArgumentException.class, new RelationStore(duplicatePending)::load);
    }
}
