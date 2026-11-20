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
package pit12.feature.client.denick;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DenickStoreTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void missingFileLoadsEmptyMappings() throws IOException {
        DenickStore cache = new DenickStore(folder.getRoot().toPath().resolve("missing.json"));
        assertTrue(cache.load().isEmpty());
    }

    @Test
    public void writesAndLoadsLowercaseNormalizedNames() throws IOException {
        Path path = folder.getRoot().toPath().resolve("cache.json");
        DenickStore cache = new DenickStore(path);
        cache.write(Collections.singletonMap("Foo_1", "0123abcdef"));
        assertEquals(Collections.singletonMap("foo_1", "0123abcdef"), cache.load());
    }

    @Test
    public void rejectsTwoEntriesForTheSameNameRegardlessOfCase() throws IOException {
        Path path = folder.getRoot().toPath().resolve("cache.json");
        Files.write(path, "{\"Foo\":\"a1\",\"foo\":\"b2\"}".getBytes(StandardCharsets.UTF_8));
        DenickStore cache = new DenickStore(path);
        assertThrows(IOException.class, cache::load);
    }

    @Test
    public void rejectsInvalidPlayerNameOrOwnerId() throws IOException {
        Path badName = folder.getRoot().toPath().resolve("bad-name.json");
        Files.write(badName, "{\"bad-name\":\"a1\"}".getBytes(StandardCharsets.UTF_8));
        Path badId = folder.getRoot().toPath().resolve("bad-id.json");
        Files.write(badId, "{\"foo\":\"not_hex\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, new DenickStore(badName)::load);
        assertThrows(IOException.class, new DenickStore(badId)::load);
    }
}
