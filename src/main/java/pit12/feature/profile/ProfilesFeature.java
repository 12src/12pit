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
package pit12.feature.profile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import pit12.feature.Feature;
import pit12.feature.profile.api.ProfileMutationResult;
import pit12.feature.profile.api.Profiles;
import pit12.feature.profile.api.ProfilesSnapshot;
import pit12.feature.profile.storage.JsonProfileStore;
import pit12.feature.profile.storage.LoadedProfiles;
import pit12.feature.profile.storage.ProfileCodec;
import pit12.feature.profile.storage.ProfileIoWorker;
import pit12.feature.profile.storage.ProfileSchema;
import pit12.feature.profile.storage.ProfileWriteBatch;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;

public final class ProfilesFeature implements Feature, Profiles, ProfileController.PersistenceSink {
    private static final Logger LOGGER = Logger.getLogger(ProfilesFeature.class.getName());
    private final ConfigCatalog catalog;
    private final Path directory;
    private final ProfileController controller;
    private final ConfigChangeListener configListener;
    private ProfileIoWorker worker;
    private boolean started;
    private long generation;

    public ProfilesFeature(ConfigCatalog catalog, Path directory) {
        this.catalog = catalog;
        this.directory = directory;
        controller = new ProfileController(catalog, this);
        configListener = controller::onConfigChanged;
    }

    @Override
    public synchronized void start() {
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
        ProfileCodec codec = new ProfileCodec(ProfileSchema.capture(catalog));
        JsonProfileStore store = new JsonProfileStore(directory, codec);
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
    public synchronized void stop() {
        if (!started && worker == null) {
            return;
        }
        started = false;
        generation++;
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
        return controller.snapshot();
    }

    @Override
    public ProfileMutationResult switchTo(UUID profileId) {
        return controller.switchTo(profileId);
    }

    @Override
    public ProfileMutationResult beginCreate() {
        return controller.beginCreate();
    }

    @Override
    public ProfileMutationResult rename(UUID profileId, String name) {
        return controller.rename(profileId, name);
    }

    @Override
    public ProfileMutationResult delete(UUID profileId) {
        return controller.delete(profileId);
    }

    @Override
    public void profileChanged(UUID ignoredProfileId) {
        requestWrite(false);
    }

    @Override
    public void activeProfileChanged() {
        requestWrite(false);
    }

    @Override
    public void profileDeleted(UUID profileId) {
        ProfileIoWorker activeWorker = worker;
        if (activeWorker != null) {
            activeWorker.requestDelete(profileId);
        }
        requestWrite(false);
    }

    public void requestImmediateWrite() {
        requestWrite(true);
    }

    private void requestWrite(boolean immediate) {
        ProfileIoWorker activeWorker = worker;
        if (activeWorker != null) {
            activeWorker.requestWrite(captureWriteBatch(), immediate);
        }
    }

    private ProfileWriteBatch captureWriteBatch() {
        return new ProfileWriteBatch(controller.dirtyProfiles(), controller.activeProfileId(),
                controller.isStateDirty());
    }

    private void dispatchToClient(long taskGeneration, Runnable task) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            synchronized (ProfilesFeature.this) {
                if (!started || generation != taskGeneration) {
                    return;
                }
            }
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
        public void stateWritten(UUID activeProfileId) {
            dispatchToClient(taskGeneration, () -> controller.statePersisted(activeProfileId));
        }

        @Override
        public void failed(String operation, Path path, IOException failure) {
            LOGGER.log(Level.WARNING, "Profile " + operation + " failed for " + path, failure);
            dispatchToClient(taskGeneration, () -> controller
                    .persistenceFailed(operation + " failed for " + path.getFileName()));
        }
    }
}
