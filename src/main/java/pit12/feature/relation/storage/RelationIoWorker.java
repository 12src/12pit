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
package pit12.feature.relation.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import pit12.feature.relation.api.RelationEntry;

public final class RelationIoWorker {
    public interface Listener {
        void loaded(List<RelationEntry> entries);

        void loadFailed(Exception failure);

        void writeFailed(IOException failure);
    }

    private final Object lock = new Object();
    private final RelationStore store;
    private final Listener listener;
    private final Thread thread;
    private List<RelationEntry> pendingWrite;
    private long writeDeadline;
    private boolean closing;

    public RelationIoWorker(RelationStore store, Listener listener) {
        this.store = store;
        this.listener = listener;
        thread = new Thread(this::run, "12pit-relations");
        thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    public void requestWrite(List<RelationEntry> entries) {
        synchronized (lock) {
            if (!closing) {
                pendingWrite = new ArrayList<RelationEntry>(entries);
                writeDeadline = System.currentTimeMillis() + 300L;
                lock.notifyAll();
            }
        }
    }

    public void closeAfter(List<RelationEntry> entries) {
        synchronized (lock) {
            if (entries != null) {
                pendingWrite = new ArrayList<RelationEntry>(entries);
            }
            closing = true;
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

    private void run() {
        try {
            listener.loaded(store.load());
        } catch (IOException | RuntimeException failure) {
            listener.loadFailed(failure);
        }
        while (true) {
            List<RelationEntry> write;
            synchronized (lock) {
                while (pendingWrite == null && !closing) {
                    try {
                        lock.wait();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (pendingWrite == null) {
                    return;
                }
                long waitMillis = writeDeadline - System.currentTimeMillis();
                if (waitMillis > 0L && !closing) {
                    try {
                        lock.wait(waitMillis);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                }
                write = pendingWrite;
                pendingWrite = null;
            }
            try {
                store.write(write);
            } catch (IOException failure) {
                listener.writeFailed(failure);
            }
        }
    }
}
