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
package pit12.feature.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import pit12.feature.profile.api.ProfileMutationResult;
import pit12.feature.profile.api.Profiles;
import pit12.feature.profile.api.ProfilesSnapshot;
import pit12.feature.profile.storage.JsonProfileStore;
import pit12.feature.profile.storage.LoadedProfiles;
import pit12.feature.profile.storage.ProfileCodec;
import pit12.feature.profile.storage.ProfileIoWorker;
import pit12.feature.profile.storage.ProfileSchema;
import pit12.feature.profile.storage.ProfileStore;
import pit12.feature.profile.storage.ProfileWriteBatch;
import pit12.feature.profile.storage.StoredProfile;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.result.OperationResult;

public final class ProfilesFeature
        implements ClientLifecycle, Profiles, ProfileController.PersistenceSink {
    private static final Logger LOGGER = Logger.getLogger(ProfilesFeature.class.getName());
    private final ConfigCatalog catalog;
    private final Path directory;
    private final Function<ProfileCodec, ProfileStore> storeFactory;
    private final ProfileController controller;
    private final ConfigChangeListener configListener;
    private final List<Runnable> listeners = new ArrayList<Runnable>();
    private ProfileIoWorker worker;
    private ProfileCodec codec;
    private boolean started;
    private long generation;

    public ProfilesFeature(ConfigCatalog catalog, Path directory) {
        this(catalog, directory, codec -> new JsonProfileStore(directory, codec));
    }

    ProfilesFeature(ConfigCatalog catalog, Path directory,
            Function<ProfileCodec, ProfileStore> storeFactory) {
        this.storeFactory = storeFactory;
        this.catalog = catalog;
        this.directory = directory;
        controller = new ProfileController(catalog, this, this::notifyListeners);
        configListener = controller::onConfigChanged;
    }

    @Override
    public void start() {
        catalog.clientThread().check();
        if (started) {
            return;
        }
        if (worker != null) {
            if (worker.isAlive()) {
                throw new IllegalStateException("Previous profile worker is still stopping");
            }
            worker = null;
        }
        started = true;
        long activeGeneration = ++generation;
        controller.beginLoading();
        codec = new ProfileCodec(ProfileSchema.capture(catalog));
        ProfileStore store = storeFactory.apply(codec);
        worker = new ProfileIoWorker(store, directory, new IoListener(activeGeneration));
        catalog.addListener(configListener);
        try {
            worker.start();
        } catch (RuntimeException failure) {
            catalog.removeListener(configListener);
            worker = null;
            started = false;
            generation++;
            throw failure;
        }
    }

    @Override
    public void stop() {
        catalog.clientThread().check();
        if (!started && worker == null) {
            return;
        }
        started = false;
        generation++;
        controller.invalidateOperations();
        catalog.removeListener(configListener);
        ProfileIoWorker closingWorker = worker;
        if (closingWorker == null) {
            return;
        }
        closingWorker.closeAfter(captureWriteBatch());
        try {
            closingWorker.awaitClose(2000L);
            if (closingWorker.isAlive()) {
                closingWorker.interrupt();
                closingWorker.awaitClose(2000L);
            }
        } catch (InterruptedException interrupted) {
            closingWorker.interrupt();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping profile worker",
                    interrupted);
        }
        if (closingWorker.isAlive()) {
            throw new IllegalStateException("Profile worker did not stop after interruption");
        }
        worker = null;
    }

    @Override
    public ProfilesSnapshot snapshot() {
        catalog.clientThread().check();
        return controller.snapshot();
    }

    @Override
    public void addListener(Runnable listener) {
        catalog.clientThread().check();
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(Runnable listener) {
        catalog.clientThread().check();
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners.toArray(new Runnable[listeners.size()])) {
            try {
                listener.run();
            } catch (RuntimeException failure) {
                LOGGER.log(Level.SEVERE, "Profile listener failed", failure);
            }
        }
    }

    @Override
    public ProfileMutationResult switchTo(UUID profileId) {
        catalog.clientThread().check();
        return started ? controller.switchTo(profileId) : unavailable();
    }

    @Override
    public ProfileMutationResult beginCreate() {
        catalog.clientThread().check();
        return started ? controller.beginCreate() : unavailable();
    }

    @Override
    public ProfileMutationResult rename(UUID profileId, String name) {
        catalog.clientThread().check();
        return started ? controller.rename(profileId, name) : unavailable();
    }

    @Override
    public ProfileMutationResult delete(UUID profileId) {
        catalog.clientThread().check();
        return started ? controller.delete(profileId) : unavailable();
    }

    private ProfileMutationResult unavailable() {
        return ProfileMutationResult.failure(ProfileMutationResult.Status.STORAGE_UNAVAILABLE,
                "Profiles are unavailable");
    }

    @Override
    public OperationResult<List<String>> exportProfiles(List<UUID> ids) {
        catalog.clientThread().check();
        if (!started || controller.snapshot().loadState() == ProfilesSnapshot.LoadState.LOADING) {
            return OperationResult.failure(OperationResult.Status.UNAVAILABLE,
                    "Profiles are still loading or unavailable");
        }
        try {
            ArrayList<String> result = new ArrayList<>();
            for (StoredProfile profile : controller.exportProfiles(ids))
                result.add(codec.encode(profile));
            return OperationResult.success(result);
        } catch (IllegalArgumentException failure) {
            return OperationResult.failure(OperationResult.Status.NOT_FOUND, failure.getMessage());
        }
    }

    @Override
    public ProfileMutationResult validateImportProfiles(List<String> profiles) {
        return importProfiles(profiles, false);
    }

    @Override
    public ProfileMutationResult importProfiles(List<String> profiles) {
        return importProfiles(profiles, true);
    }

    private ProfileMutationResult importProfiles(List<String> profiles, boolean apply) {
        catalog.clientThread().check();
        if (!started)
            return unavailable();
        if (controller.snapshot().loadState() == ProfilesSnapshot.LoadState.LOADING) {
            return ProfileMutationResult.failure(ProfileMutationResult.Status.LOADING,
                    "Profiles are still loading");
        }
        if (controller.snapshot().loadState() != ProfilesSnapshot.LoadState.READY) {
            return ProfileMutationResult.failure(ProfileMutationResult.Status.STORAGE_UNAVAILABLE,
                    "Profiles are not ready for import");
        }
        try {
            List<StoredProfile> decoded = decodeProfiles(profiles);
            if (apply)
                controller.importProfiles(decoded);
            else
                controller.validateImported(decoded);
            return ProfileMutationResult.success();
        } catch (JsonParseException | IllegalArgumentException failure) {
            return ProfileMutationResult.failure(ProfileMutationResult.Status.INVALID_VALUE,
                    failure.getMessage());
        }
    }

    private List<StoredProfile> decodeProfiles(List<String> profiles) {
        ArrayList<StoredProfile> decoded = new ArrayList<StoredProfile>();
        for (String text : profiles) {
            JsonElement parsed = new JsonParser().parse(text);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Profile must be an object");
            }
            JsonElement id = parsed.getAsJsonObject().get("id");
            if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Profile ID is missing");
            }
            ProfileCodec.DecodeResult result =
                    codec.decode(UUID.fromString(id.getAsString()), new StringReader(text));
            if (!result.warnings().isEmpty()) {
                throw new IllegalArgumentException("Invalid profile: " + result.warnings().get(0));
            }
            decoded.add(result.profile());
        }
        return decoded;
    }

    @Override
    public void changed() {
        catalog.clientThread().check();
        worker.requestWrite(captureWriteBatch());
    }

    @Override
    public void profileDeleted(UUID profileId) {
        catalog.clientThread().check();
        worker.requestDelete(profileId);
        changed();
    }

    private ProfileWriteBatch captureWriteBatch() {
        return new ProfileWriteBatch(controller.dirtyProfiles(), controller.activeProfileId(),
                controller.isStateDirty(), controller.stateRevision());
    }

    private void dispatchToClient(long taskGeneration, Runnable task) {
        catalog.clientThread().execute(() -> {
            if (!started || generation != taskGeneration)
                return;
            task.run();
        });
    }

    private final class IoListener implements ProfileIoWorker.Listener {
        private final long taskGeneration;

        private IoListener(long taskGeneration) {
            this.taskGeneration = taskGeneration;
        }

        @Override
        public void loaded(LoadedProfiles profiles) {
            // Initialization returns before scheduled client tasks execute, so dependent features are started first.
            dispatchToClient(taskGeneration, () -> controller.applyLoaded(profiles));
        }

        @Override
        public void profileWritten(UUID profileId, long revision) {
            dispatchToClient(taskGeneration, () -> controller.persisted(profileId, revision));
        }

        @Override
        public void stateWritten(UUID activeProfileId, long revision) {
            dispatchToClient(taskGeneration,
                    () -> controller.statePersisted(activeProfileId, revision));
        }

        @Override
        public void failed(String operation, Path path, IOException failure) {
            LOGGER.log(Level.WARNING, "Profile " + operation + " failed for " + path, failure);
            dispatchToClient(taskGeneration, () -> controller
                    .persistenceFailed(operation + " failed for " + path.getFileName()));
        }
    }
}
