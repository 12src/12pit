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
package pit12.feature.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import pit12.feature.profile.api.ProfileMutationResult;
import pit12.feature.profile.api.ProfileMutationResult.Status;
import pit12.feature.profile.api.ProfileSummary;
import pit12.feature.profile.api.ProfilesSnapshot;
import pit12.feature.profile.api.ProfilesSnapshot.LoadState;
import pit12.feature.profile.storage.LoadedProfiles;
import pit12.feature.profile.storage.ProfileLoadProblem;
import pit12.feature.profile.storage.StoredProfile;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.ConfigSnapshot;

final class ProfileController {
    interface PersistenceSink {
        void profileChanged(UUID profileId);

        void activeProfileChanged();

        void profileDeleted(UUID profileId);
    }

    private final ConfigCatalog catalog;
    private final PersistenceSink persistence;
    private final List<ProfileRecord> profiles = new ArrayList<ProfileRecord>();
    private final Map<UUID, ProfileRecord> profilesById = new LinkedHashMap<UUID, ProfileRecord>();
    private final List<String> problems = new ArrayList<String>();
    private ProfilesSnapshot snapshot = ProfilesSnapshot.loading();
    private LoadState loadState = LoadState.LOADING;
    private UUID activeProfileId;
    private long revision;
    private boolean applyingProfile;
    private boolean stateDirty;

    ProfileController(ConfigCatalog catalog, PersistenceSink persistence) {
        this.catalog = catalog;
        this.persistence = persistence;
    }

    ProfilesSnapshot snapshot() {
        return snapshot;
    }

    void beginLoading() {
        loadState = LoadState.LOADING;
        publish();
    }

    void applyLoaded(LoadedProfiles loaded) {
        profiles.clear();
        profilesById.clear();
        problems.clear();
        stateDirty = false;
        for (ProfileLoadProblem problem : loaded.problems()) {
            problems.add(problem.summary());
        }
        ArrayList<StoredProfile> ordered = new ArrayList<StoredProfile>(loaded.profiles());
        Collections.sort(ordered,
                Comparator.comparingInt(StoredProfile::order).thenComparing(StoredProfile::id));
        for (StoredProfile stored : ordered) {
            if (profilesById.containsKey(stored.id())) {
                problems.add(stored.id() + ": duplicate profile ID");
                continue;
            }
            if (findByName(stored.name(), null) != null) {
                problems.add(stored.id() + ": duplicate profile name " + stored.name());
                continue;
            }
            ConfigSnapshot normalized;
            try {
                normalized = catalog.normalize(stored.config());
            } catch (IllegalArgumentException failure) {
                problems.add(stored.id() + ": " + failure.getMessage());
                normalized = catalog.defaults();
            }
            ProfileRecord record = new ProfileRecord(stored, normalized);
            profiles.add(record);
            profilesById.put(record.id(), record);
        }
        if (profiles.isEmpty()) {
            ProfileRecord defaultProfile =
                    new ProfileRecord(UUID.randomUUID(), "Default", 0, catalog.defaults());
            profiles.add(defaultProfile);
            profilesById.put(defaultProfile.id(), defaultProfile);
            activeProfileId = defaultProfile.id();
            stateDirty = true;
        } else {
            activeProfileId =
                    profilesById.containsKey(loaded.activeProfileId()) ? loaded.activeProfileId()
                            : profiles.get(0).id();
            if (!activeProfileId.equals(loaded.activeProfileId())) {
                stateDirty = true;
            }
        }
        normalizeOrders();
        ProfileRecord active = profilesById.get(activeProfileId);
        applyingProfile = true;
        try {
            catalog.apply(active.config());
        } finally {
            applyingProfile = false;
        }
        loadState = loaded.storageAvailable() && problems.isEmpty() ? LoadState.READY
                : LoadState.DEGRADED;
        publish();
        for (ProfileRecord record : profiles) {
            if (record.dirty()) {
                persistence.profileChanged(record.id());
            }
        }
        if (stateDirty) {
            persistence.activeProfileChanged();
        }
    }

    void onConfigChanged(ConfigChangeSet ignoredChanges) {
        if (applyingProfile || loadState == LoadState.LOADING) {
            return;
        }
        ProfileRecord active = profilesById.get(activeProfileId);
        if (active == null) {
            return;
        }
        active.config(catalog.snapshot());
        markChanged(active);
        publish();
        persistence.profileChanged(active.id());
    }

    ProfileMutationResult switchTo(UUID profileId) {
        ProfileMutationResult ready = requireLoaded();
        if (ready != null) {
            return ready;
        }
        ProfileRecord target = profilesById.get(profileId);
        if (target == null) {
            return failure(Status.NOT_FOUND, "Profile was not found");
        }
        if (target.id().equals(activeProfileId)) {
            return ProfileMutationResult.success();
        }
        ProfileRecord previous = profilesById.get(activeProfileId);
        previous.config(catalog.snapshot());
        markChanged(previous);
        applyingProfile = true;
        try {
            catalog.apply(target.config());
        } finally {
            applyingProfile = false;
        }
        activeProfileId = target.id();
        stateDirty = true;
        publish();
        persistence.profileChanged(previous.id());
        if (target.dirty()) {
            persistence.profileChanged(target.id());
        }
        persistence.activeProfileChanged();
        return ProfileMutationResult.success();
    }

    ProfileMutationResult beginCreate() {
        ProfileMutationResult ready = requireLoaded();
        if (ready != null) {
            return ready;
        }
        ProfileRecord active = profilesById.get(activeProfileId);
        active.config(catalog.snapshot());
        ProfileCreateOperation operation =
                new ProfileCreateOperation(this, UUID.randomUUID(), active.config());
        return ProfileMutationResult.create(operation);
    }

    ProfileMutationResult rename(UUID profileId, String requestedName) {
        ProfileRecord record = profilesById.get(profileId);
        if (record == null) {
            return loadState == LoadState.LOADING
                    ? failure(Status.LOADING, "Profiles are still loading")
                    : failure(Status.NOT_FOUND, "Profile was not found");
        }
        String validated = validatedName(requestedName);
        if (validated == null) {
            return failure(Status.INVALID_NAME, "Name must contain 1 to 48 characters");
        }
        if (findByName(validated, profileId) != null) {
            return failure(Status.DUPLICATE_NAME, "Another profile already uses that name");
        }
        if (record.name().equals(validated)) {
            return ProfileMutationResult.success();
        }
        record.name(validated);
        markChanged(record);
        publish();
        persistence.profileChanged(record.id());
        return ProfileMutationResult.success();
    }

    ProfileMutationResult delete(UUID profileId) {
        ProfileRecord record = profilesById.get(profileId);
        if (record == null) {
            return loadState == LoadState.LOADING
                    ? failure(Status.LOADING, "Profiles are still loading")
                    : failure(Status.NOT_FOUND, "Profile was not found");
        }
        if (profileId.equals(activeProfileId)) {
            return failure(Status.ACTIVE_PROFILE_PROTECTED, "The active profile cannot be deleted");
        }
        profiles.remove(record);
        profilesById.remove(profileId);
        List<ProfileRecord> changed = normalizeOrders();
        publish();
        persistence.profileDeleted(profileId);
        for (ProfileRecord changedRecord : changed) {
            persistence.profileChanged(changedRecord.id());
        }
        return ProfileMutationResult.success();
    }

    ProfileMutationResult commit(ProfileCreateOperation operation) {
        String name = validatedName(operation.name());
        if (name == null) {
            return failure(Status.INVALID_NAME, "Name must contain 1 to 48 characters");
        }
        if (findByName(name, null) != null) {
            return failure(Status.DUPLICATE_NAME, "Another profile already uses that name");
        }
        ConfigSnapshot config;
        try {
            config = catalog.normalize(operation.config());
        } catch (IllegalArgumentException failure) {
            return failure(Status.INVALID_VALUE, failure.getMessage());
        }
        ProfileRecord previous = profilesById.get(activeProfileId);
        previous.config(catalog.snapshot());
        markChanged(previous);
        ProfileRecord created = new ProfileRecord(operation.profileId(), name, 0, config);
        profiles.add(0, created);
        profilesById.put(created.id(), created);
        List<ProfileRecord> reordered = normalizeOrders();
        applyingProfile = true;
        try {
            catalog.apply(config);
        } finally {
            applyingProfile = false;
        }
        activeProfileId = created.id();
        stateDirty = true;
        publish();
        persistence.profileChanged(previous.id());
        persistence.profileChanged(created.id());
        for (ProfileRecord reorderedRecord : reordered) {
            if (!reorderedRecord.id().equals(created.id())
                    && !reorderedRecord.id().equals(previous.id())) {
                persistence.profileChanged(reorderedRecord.id());
            }
        }
        persistence.activeProfileChanged();
        return ProfileMutationResult.success();
    }

    List<StoredProfile> dirtyProfiles() {
        ArrayList<StoredProfile> result = new ArrayList<StoredProfile>();
        for (ProfileRecord record : profiles) {
            if (record.dirty()) {
                result.add(record.stored());
            }
        }
        return result;
    }

    UUID activeProfileId() {
        return activeProfileId;
    }

    boolean isStateDirty() {
        return stateDirty;
    }

    void persisted(UUID profileId, long persistedRevision) {
        ProfileRecord record = profilesById.get(profileId);
        if (record != null && record.dirty()) {
            record.persisted(persistedRevision);
            publish();
        }
    }

    void statePersisted(UUID persistedActiveProfileId) {
        if (persistedActiveProfileId.equals(activeProfileId)) {
            stateDirty = false;
            publish();
        }
    }

    void persistenceFailed(String message) {
        if (!problems.contains(message)) {
            problems.add(message);
        }
        loadState = LoadState.DEGRADED;
        publish();
    }

    private List<ProfileRecord> normalizeOrders() {
        ArrayList<ProfileRecord> changed = new ArrayList<ProfileRecord>();
        for (int index = 0; index < profiles.size(); index++) {
            ProfileRecord record = profiles.get(index);
            if (record.order() != index) {
                record.order(index);
                markChanged(record);
                changed.add(record);
            }
        }
        return changed;
    }

    private void markChanged(ProfileRecord record) {
        record.touch();
    }

    private void publish() {
        ArrayList<ProfileSummary> summaries = new ArrayList<ProfileSummary>(profiles.size());
        boolean dirty = stateDirty;
        for (ProfileRecord record : profiles) {
            summaries.add(record.summary());
            dirty |= record.dirty();
        }
        snapshot = new ProfilesSnapshot(loadState, ++revision, activeProfileId, summaries, problems,
                dirty);
    }

    private ProfileMutationResult requireLoaded() {
        return loadState == LoadState.LOADING
                ? failure(Status.LOADING, "Profiles are still loading")
                : null;
    }

    private ProfileRecord findByName(String name, UUID excludedId) {
        String normalized = name.toLowerCase(Locale.ROOT);
        for (ProfileRecord profile : profiles) {
            if ((excludedId == null || !excludedId.equals(profile.id()))
                    && profile.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return profile;
            }
        }
        return null;
    }

    private static String validatedName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() || trimmed.length() > 48 ? null : trimmed;
    }

    private static ProfileMutationResult failure(Status status, String message) {
        return ProfileMutationResult.failure(status, message);
    }
}
