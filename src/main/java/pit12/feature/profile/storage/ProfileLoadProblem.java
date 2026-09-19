/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 12dev <https://github.com/12src>
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
package pit12.feature.profile.storage;

import java.nio.file.Path;

public final class ProfileLoadProblem {
    private final Path path;
    private final String message;

    public ProfileLoadProblem(Path path, String message) {
        this.path = path;
        this.message = message;
    }

    public Path path() {
        return path;
    }

    public String message() {
        return message;
    }

    public String summary() {
        return path == null ? message : path.getFileName() + ": " + message;
    }
}
