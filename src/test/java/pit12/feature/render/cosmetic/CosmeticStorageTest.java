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
package pit12.feature.render.cosmetic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class CosmeticStorageTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void storesModelsInTheDataDirectory() {
        CosmeticStorage storage = new CosmeticStorage(folder.getRoot().toPath());
        assertNull(storage.read("plain"));
        storage.write("plain", "{\"kind\":\"voxels\"}");
        assertEquals("{\"kind\":\"voxels\"}", storage.read("plain"));
        assertTrue(new File(folder.getRoot(), "plain.json").isFile());
    }

    @Test
    public void ignoresInvalidIds() {
        CosmeticStorage storage = new CosmeticStorage(folder.getRoot().toPath());
        assertNull(storage.read("../plain"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidIdsOnWrite() {
        CosmeticStorage storage = new CosmeticStorage(folder.getRoot().toPath());
        storage.write("../plain", "{}");
    }
}
