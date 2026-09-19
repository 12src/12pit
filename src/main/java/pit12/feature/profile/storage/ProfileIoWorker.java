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
package pit12.feature.profile.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class ProfileIoWorker implements AutoCloseable {
    public interface Listener {
        void loaded(LoadedProfiles profiles);

        void profileWritten(UUID profileId, long revision);

        void stateWritten(UUID activeProfileId);

        void failed(String operation, Path path, IOException failure);
    }

    private static final long SAVE_DEBOUNCE_MILLIS = 300L;
    private final Object lock = new Object();
    private final JsonProfileStore store;
    private final Listener listener;
    private final Path directory;
    private final Thread thread;
    private final Set<UUID> pendingDeletes = new LinkedHashSet<UUID>();
    private boolean loadPending = true;
    private ProfileWriteBatch pendingWrite;
    private long writeDeadline;
    private boolean closeRequested;

    public ProfileIoWorker(JsonProfileStore store, Path directory, Listener listener) {
        this.store = store;
        this.listener = listener;
        this.directory = directory;
        thread = new Thread(this::run, "12pit-profiles");
        thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    public void requestWrite(ProfileWriteBatch batch, boolean immediate) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        synchronized (lock) {
            if (closeRequested) {
                return;
            }
            pendingWrite = batch;
            writeDeadline = immediate ? 0L : System.currentTimeMillis() + SAVE_DEBOUNCE_MILLIS;
            lock.notifyAll();
        }
    }

    public void requestDelete(UUID profileId) {
        synchronized (lock) {
            if (closeRequested) {
                return;
            }
            pendingDeletes.add(profileId);
            lock.notifyAll();
        }
    }

    public void closeAfter(ProfileWriteBatch finalWrite) {
        synchronized (lock) {
            if (finalWrite != null && !finalWrite.isEmpty()) {
                pendingWrite = finalWrite;
                writeDeadline = 0L;
            }
            closeRequested = true;
            lock.notifyAll();
        }
    }

    public void awaitClose(long timeoutMillis) throws InterruptedException {
        thread.join(timeoutMillis);
    }

    public boolean isAlive() {
        return thread.isAlive();
    }

    public void interrupt() {
        thread.interrupt();
    }

    @Override
    public void close() {
        closeAfter(null);
    }

    private void run() {
        while (true) {
            Work work;
            synchronized (lock) {
                work = awaitWork();
                if (work == null) {
                    return;
                }
            }
            if (work.load) {
                listener.loaded(store.load());
            }
            for (UUID profileId : work.deletes) {
                try {
                    store.moveToTrash(profileId);
                } catch (IOException failure) {
                    listener.failed("delete", directory.resolve(profileId.toString() + ".json"),
                            failure);
                }
            }
            if (work.write != null) {
                write(work.write);
            }
        }
    }

    private Work awaitWork() {
        while (true) {
            if (loadPending) {
                loadPending = false;
                return Work.load();
            }
            if (!pendingDeletes.isEmpty()) {
                ArrayList<UUID> deletes = new ArrayList<UUID>(pendingDeletes);
                pendingDeletes.clear();
                return Work.deletes(deletes);
            }
            if (pendingWrite != null) {
                long waitMillis = writeDeadline - System.currentTimeMillis();
                if (waitMillis <= 0L || closeRequested) {
                    ProfileWriteBatch batch = pendingWrite;
                    pendingWrite = null;
                    return Work.write(batch);
                }
                if (!waitForSignal(waitMillis)) {
                    return null;
                }
                continue;
            }
            if (closeRequested) {
                return null;
            }
            if (!waitForSignal(0L)) {
                return null;
            }
        }
    }

    private boolean waitForSignal(long timeoutMillis) {
        try {
            if (timeoutMillis > 0L) {
                lock.wait(timeoutMillis);
            } else {
                lock.wait();
            }
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void write(ProfileWriteBatch batch) {
        for (StoredProfile profile : batch.profiles()) {
            try {
                store.writeProfile(profile);
                listener.profileWritten(profile.id(), profile.revision());
            } catch (IOException failure) {
                listener.failed("write profile",
                        directory.resolve(profile.id().toString() + ".json"), failure);
            }
        }
        if (batch.isStateDirty() && batch.activeProfileId() != null) {
            try {
                store.writeState(batch.activeProfileId());
                listener.stateWritten(batch.activeProfileId());
            } catch (IOException failure) {
                listener.failed("write state", directory.resolve("profile-state.json"), failure);
            }
        }
    }

    private static final class Work {
        private final boolean load;
        private final ArrayList<UUID> deletes;
        private final ProfileWriteBatch write;

        private Work(boolean load, ArrayList<UUID> deletes, ProfileWriteBatch write) {
            this.load = load;
            this.deletes = deletes;
            this.write = write;
        }

        static Work load() {
            return new Work(true, new ArrayList<UUID>(), null);
        }

        static Work deletes(ArrayList<UUID> deletes) {
            return new Work(false, deletes, null);
        }

        static Work write(ProfileWriteBatch write) {
            return new Work(false, new ArrayList<UUID>(), write);
        }
    }
}
