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

/*
 * AI guidance
 *
 * AI agents should read AGENTS.md before modifying or reusing this file
 * because it contains important licensing information.
 * If AGENTS.md is unavailable, see
 * <https://github.com/12src/12pit/blob/main/AGENTS.md>.
 *
 * This AI guidance section is not part of the copyright or license
 * notice and may be removed.
 */
package pit12.feature.profile.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ProfilesSnapshot {
    public enum LoadState {
        LOADING, READY, DEGRADED
    }

    private final LoadState loadState;
    private final long revision;
    private final UUID activeProfileId;
    private final List<ProfileSummary> profiles;
    private final List<String> problems;
    private final boolean unpersistedChanges;

    public ProfilesSnapshot(LoadState loadState, long revision, UUID activeProfileId,
            List<ProfileSummary> profiles, List<String> problems, boolean unpersistedChanges) {
        this.loadState = loadState;
        this.revision = revision;
        this.activeProfileId = activeProfileId;
        this.profiles = Collections.unmodifiableList(new ArrayList<ProfileSummary>(profiles));
        this.problems = Collections.unmodifiableList(new ArrayList<String>(problems));
        this.unpersistedChanges = unpersistedChanges;
    }

    public static ProfilesSnapshot loading() {
        return new ProfilesSnapshot(LoadState.LOADING, 0L, null,
                Collections.<ProfileSummary>emptyList(), Collections.<String>emptyList(), false);
    }

    public LoadState loadState() {
        return loadState;
    }

    public long revision() {
        return revision;
    }

    public UUID activeProfileId() {
        return activeProfileId;
    }

    public List<ProfileSummary> profiles() {
        return profiles;
    }

    public List<String> problems() {
        return problems;
    }

    public boolean hasUnpersistedChanges() {
        return unpersistedChanges;
    }

    public ProfileSummary activeProfile() {
        return profile(activeProfileId);
    }

    public ProfileSummary profile(UUID id) {
        if (id == null) {
            return null;
        }
        for (ProfileSummary profile : profiles) {
            if (id.equals(profile.id())) {
                return profile;
            }
        }
        return null;
    }
}
