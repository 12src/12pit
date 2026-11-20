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
package pit12.runtime.config.profile.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class JsonProfileStoreTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aCorruptProfileDoesNotBlockOtherProfilesOrChangeTheFile() throws Exception {
        Path directory = folder.getRoot().toPath();
        UUID good = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Files.write(directory.resolve(good + ".json"),
                profileJson(good, "Foo").getBytes(StandardCharsets.UTF_8));
        Path bad = directory.resolve("00000000-0000-0000-0000-000000000002.json");
        byte[] original = "{broken".getBytes(StandardCharsets.UTF_8);
        Files.write(bad, original);
        JsonProfileStore store = store(directory);
        LoadedProfiles result = store.load();
        assertTrue(result.storageAvailable());
        assertEquals(1, result.profiles().size());
        assertEquals(good, result.profiles().get(0).id());
        assertEquals(1, result.problems().size());
        assertTrue(result.problems().get(0).summary().startsWith(bad.getFileName() + ":"));
        assertArrayEquals(original, Files.readAllBytes(bad));
    }

    @Test
    public void isolatesProfileWhoseIdDoesNotMatchFilenameAndRecoversFromCorruptStateFile()
            throws Exception {
        Path directory = folder.getRoot().toPath();
        UUID good = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID wrongContentId = UUID.fromString("00000000-0000-0000-0000-000000000009");
        Files.write(directory.resolve(good + ".json"),
                profileJson(good, "Foo").getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("00000000-0000-0000-0000-000000000002.json"),
                profileJson(wrongContentId, "Bar").getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("profiles.json"),
                "{\"schemaVersion\":99}".getBytes(StandardCharsets.UTF_8));
        JsonProfileStore store = store(directory);
        LoadedProfiles loaded = store.load();
        assertNull(loaded.activeProfileId());
        assertEquals(1, loaded.profiles().size());
        assertEquals(good, loaded.profiles().get(0).id());
        assertEquals(2, loaded.problems().size());
    }

    @Test
    public void writesActiveStateAndMovesDeletedProfilesIntoTrash() throws Exception {
        Path directory = folder.getRoot().toPath();
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Path source = directory.resolve(id + ".json");
        Files.write(source, profileJson(id, "Foo").getBytes(StandardCharsets.UTF_8));
        JsonProfileStore store = store(directory);
        store.writeState(id);
        assertEquals(id, store.load().activeProfileId());
        store.moveToTrash(id);
        store.moveToTrash(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        assertFalse(Files.exists(source));
        List<Path> trashed = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory.resolve("trash"))) {
            for (Path entry : entries) {
                trashed.add(entry);
            }
        }
        assertEquals(1, trashed.size());
    }

    private static JsonProfileStore store(Path directory) {
        return new JsonProfileStore(directory, new ProfileCodec(
                new ProfileSchema(Collections.emptyMap()), new LegacyProfileSettings()));
    }

    private static String profileJson(UUID id, String name) {
        return "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"name\":\"" + name
                + "\",\"order\":0,\"features\":{}}";
    }
}
