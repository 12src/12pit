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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class CosmeticStorage {
    private static final Logger LOGGER = LogManager.getLogger(CosmeticStorage.class);
    private static final String ID = "[a-z0-9_]{1,32}";
    private final Path directory;

    CosmeticStorage(Path directory) {
        this.directory = directory;
    }

    String read(String id) {
        if (!id.matches(ID)) {
            return null;
        }
        Path file = directory.resolve(id + ".json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            LOGGER.warn("Could not read cosmetic model {}", file, failure);
            return null;
        }
    }

    void write(String id, String json) {
        if (!id.matches(ID)) {
            throw new IllegalArgumentException("Invalid model id");
        }
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(id + ".json"), json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save the model", failure);
        }
    }
}
