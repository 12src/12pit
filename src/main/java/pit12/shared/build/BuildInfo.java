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
package pit12.shared.build;

public final class BuildInfo {
    private final String modName;
    private final String version;
    private final String gitCommit;
    private final boolean release;

    public BuildInfo(String modName, String version, String gitCommit, boolean release) {
        this.modName = modName;
        this.version = version;
        this.gitCommit = gitCommit;
        this.release = release;
    }

    public String modName() {
        return modName;
    }

    public String version() {
        return version;
    }

    public String gitCommit() {
        return gitCommit;
    }

    public boolean isRelease() {
        return release;
    }
}
