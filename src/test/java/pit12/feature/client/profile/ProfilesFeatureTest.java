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
package pit12.feature.client.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import pit12.feature.client.profile.api.ProfileCreateSession;
import pit12.feature.client.profile.api.ProfileMutationResult.Status;
import pit12.feature.client.profile.api.ProfileSummary;
import pit12.feature.client.profile.api.Profiles;
import pit12.feature.client.profile.api.ProfilesSnapshot.LoadState;
import pit12.feature.client.profile.storage.LoadedProfiles;
import pit12.feature.client.profile.storage.ProfileStore;
import pit12.feature.client.profile.storage.StoredProfile;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigSnapshot;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.result.OperationResult;

public final class ProfilesFeatureTest {
    private final Thread clientThread = Thread.currentThread();
    private final BlockingQueue<Runnable> clientTasks = new LinkedBlockingQueue<>();
    private final ConfigCatalog catalog = new ConfigCatalog(
            new ClientThread(() -> Thread.currentThread() == clientThread, clientTasks::add));
    private final StubConfig config = new StubConfig();
    private final UUID firstId = UUID.randomUUID();
    private final UUID secondId = UUID.randomUUID();
    private final Store store = new Store();
    private final ProfilesFeature feature =
            new ProfilesFeature(catalog, Paths.get("profiles"), codec -> store);
    private final Profiles profiles = feature;

    @Before
    public void loadProfiles() throws InterruptedException {
        catalog.register(config);
        catalog.freeze();
        store.loaded = new LoadedProfiles(
                Arrays.asList(savedProfile(firstId, "First", 0, 2),
                        savedProfile(secondId, "Second", 1, 8)),
                firstId, Collections.emptyList(), true);
        feature.start();
        awaitClient(() -> profiles.snapshot().loadState() != LoadState.LOADING);
        assertEquals(LoadState.READY, profiles.snapshot().loadState());
        assertFalse(profiles.snapshot().hasUnpersistedChanges());
    }

    @After
    public void stopProfiles() {
        store.holdWrites = false;
        store.failWrites = false;
        store.writePermits.release();
        feature.stop();
    }

    @Test
    public void switchingProfilesPreservesEachProfilesEdits() {
        config.limit.set(4);
        assertTrue(profiles.switchTo(secondId).succeeded());
        assertEquals(secondId, profiles.snapshot().activeProfileId());
        assertEquals(8, (int) config.limit.get());
        config.limit.set(9);
        assertTrue(profiles.switchTo(firstId).succeeded());
        assertEquals(firstId, profiles.snapshot().activeProfileId());
        assertEquals(4, (int) config.limit.get());
        assertTrue(profiles.switchTo(secondId).succeeded());
        assertEquals(9, (int) config.limit.get());
    }

    @Test
    public void duplicateNamesLeaveExistingProfilesAndValuesIntact() {
        config.limit.set(4);
        Map<UUID, String> originalNames = profileNames();
        assertEquals(Status.DUPLICATE_NAME, profiles.rename(secondId, "FIRST").status());
        assertEquals(originalNames, profileNames());
        ProfileCreateSession create = profiles.beginCreate().createSession();
        assertNotNull(create);
        create.setName("second");
        assertEquals(Status.DUPLICATE_NAME, create.commit().status());
        create.cancel();
        assertEquals(originalNames, profileNames());
        assertEquals(firstId, profiles.snapshot().activeProfileId());
        assertEquals(4, (int) config.limit.get());
    }

    @Test
    public void anInvalidImportPreservesAllExistingProfilesAndValues() {
        OperationResult<List<String>> exported =
                profiles.exportProfiles(Collections.singletonList(firstId));
        assertTrue(exported.succeeded());
        Map<UUID, String> originalNames = profileNames();
        config.limit.set(4);
        assertEquals(Status.INVALID_VALUE,
                profiles.importProfiles(Arrays.asList(exported.value().get(0), "{")).status());
        assertEquals(originalNames, profileNames());
        assertEquals(firstId, profiles.snapshot().activeProfileId());
        assertEquals(4, (int) config.limit.get());
        assertTrue(profiles.switchTo(secondId).succeeded());
        assertEquals(8, (int) config.limit.get());
        assertTrue(profiles.switchTo(firstId).succeeded());
        assertEquals(4, (int) config.limit.get());
    }

    @Test
    public void anOlderSaveLeavesNewerEditsPendingUntilTheyAreSaved() throws InterruptedException {
        store.holdWrites = true;
        config.limit.set(4);
        StoredProfile olderWrite = nextWrite();
        assertEquals(4, olderWrite.config().feature(config.id()).get("limit"));
        config.limit.set(6);
        store.writePermits.release();
        StoredProfile newerWrite = nextWrite();
        assertEquals(6, newerWrite.config().feature(config.id()).get("limit"));
        Runnable task;
        while ((task = clientTasks.poll()) != null) {
            task.run();
        }
        assertTrue(profiles.snapshot().hasUnpersistedChanges());
        store.writePermits.release();
        awaitClient(() -> !profiles.snapshot().hasUnpersistedChanges());
        assertFalse(profiles.snapshot().hasUnpersistedChanges());
        assertEquals(6, (int) config.limit.get());
    }

    @Test
    public void aFailedSaveKeepsTheEditsPendingAndReportsAProblem() throws InterruptedException {
        store.failWrites = true;
        config.limit.set(4);
        nextWrite();
        awaitClient(() -> !profiles.snapshot().problems().isEmpty());
        assertTrue(profiles.snapshot().hasUnpersistedChanges());
        assertFalse(profiles.snapshot().problems().isEmpty());
        assertEquals(4, (int) config.limit.get());
    }

    private StoredProfile savedProfile(UUID id, String name, int order, int limit) {
        ConfigSnapshot values = new ConfigSnapshot(
                Collections.singletonMap(config.id(), Collections.singletonMap("limit", limit)));
        return new StoredProfile(id, name, order, 0L, 0L, values, new JsonObject());
    }

    private Map<UUID, String> profileNames() {
        Map<UUID, String> names = new HashMap<>();
        for (ProfileSummary profile : profiles.snapshot().profiles()) {
            names.put(profile.id(), profile.name());
        }
        return names;
    }

    private void awaitClient(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            long remaining = deadline - System.nanoTime();
            assertTrue("Timed out waiting for profile state", remaining > 0L);
            Runnable task = clientTasks.poll(remaining, TimeUnit.NANOSECONDS);
            assertNotNull("Expected a client task", task);
            task.run();
        }
    }

    private StoredProfile nextWrite() throws InterruptedException {
        StoredProfile profile = store.writes.poll(5, TimeUnit.SECONDS);
        assertNotNull("Expected a profile write", profile);
        return profile;
    }

    private static final class StubConfig extends FeatureConfig {
        final IntegerSetting limit = integerSliderSetting("limit", "Limit", "", 2, 0, 10, 1);

        StubConfig() {
            super("sample", "Sample", null, "");
        }
    }
    private static final class Store implements ProfileStore {
        final BlockingQueue<StoredProfile> writes = new LinkedBlockingQueue<>();
        final Semaphore writePermits = new Semaphore(0);
        LoadedProfiles loaded;
        volatile boolean holdWrites;
        volatile boolean failWrites;

        @Override
        public LoadedProfiles load() {
            return loaded;
        }

        @Override
        public void writeProfile(StoredProfile profile) throws IOException {
            writes.add(profile);
            if (holdWrites) {
                try {
                    writePermits.acquire();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Profile write interrupted", interrupted);
                }
            }
            if (failWrites) {
                throw new IOException("Profile write failed");
            }
        }

        @Override
        public void writeState(UUID activeProfileId) {}

        @Override
        public void moveToTrash(UUID profileId) {}
    }
}
