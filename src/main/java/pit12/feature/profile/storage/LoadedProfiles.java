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
package pit12.feature.profile.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class LoadedProfiles {
    private final List<StoredProfile> profiles;
    private final UUID activeProfileId;
    private final List<ProfileLoadProblem> problems;
    private final boolean storageAvailable;

    public LoadedProfiles(List<StoredProfile> profiles, UUID activeProfileId,
            List<ProfileLoadProblem> problems, boolean storageAvailable) {
        this.profiles = Collections.unmodifiableList(new ArrayList<StoredProfile>(profiles));
        this.activeProfileId = activeProfileId;
        this.problems = Collections.unmodifiableList(new ArrayList<ProfileLoadProblem>(problems));
        this.storageAvailable = storageAvailable;
    }

    public List<StoredProfile> profiles() {
        return profiles;
    }

    public UUID activeProfileId() {
        return activeProfileId;
    }

    public List<ProfileLoadProblem> problems() {
        return problems;
    }

    public boolean storageAvailable() {
        return storageAvailable;
    }
}
