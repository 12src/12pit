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
package pit12.feature.relation.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationEntry;

public final class RelationStoreTest {
    @Rule
    public final TemporaryFolder files = new TemporaryFolder();

    @Test
    public void pendingIdentitySurvivesSaveAndLoad() throws Exception {
        Path path = files.getRoot().toPath().resolve("relations.json");
        RelationStore store = new RelationStore(path);
        UUID knownId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        store.write(Arrays.asList(new RelationEntry(null, "PendingPlayer", Relation.FRIEND),
                new RelationEntry(knownId, "KnownPlayer", Relation.ENEMY)));
        JsonObject pending =
                new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        assertFalse(pending.has("uuid"));
        List<RelationEntry> loaded = store.load();
        assertEquals(2, loaded.size());
        assertNull(loaded.get(0).playerId());
        assertEquals("PendingPlayer", loaded.get(0).name());
        assertEquals(Relation.FRIEND, loaded.get(0).relation());
        assertEquals(knownId, loaded.get(1).playerId());
        assertEquals("KnownPlayer", loaded.get(1).name());
        assertEquals(Relation.ENEMY, loaded.get(1).relation());
    }

    @Test
    public void loadsPendingIdentityWithoutUuid() throws Exception {
        Path path = files.getRoot().toPath().resolve("relations.json");
        Files.write(path,
                ("{\"schemaVersion\":1,\"entries\":["
                        + "{\"name\":\"PendingPlayer\",\"relation\":\"FRIEND\"}]}")
                        .getBytes(StandardCharsets.UTF_8));
        List<RelationEntry> loaded = new RelationStore(path).load();
        assertEquals(1, loaded.size());
        assertNull(loaded.get(0).playerId());
        assertEquals("PendingPlayer", loaded.get(0).name());
        assertEquals(Relation.FRIEND, loaded.get(0).relation());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedUuid() throws Exception {
        Path path = files.getRoot().toPath().resolve("relations.json");
        Files.write(path,
                ("{\"schemaVersion\":1,\"entries\":["
                        + "{\"uuid\":\"invalid\",\"name\":\"Player\",\"relation\":\"ENEMY\"}]}")
                        .getBytes(StandardCharsets.UTF_8));
        new RelationStore(path).load();
    }
}
