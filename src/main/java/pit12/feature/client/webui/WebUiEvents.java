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
package pit12.feature.client.webui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

final class WebUiEvents {
    static final int MAX_STREAMS = 8;

    static final class Stream {
        final BlockingQueue<Boolean> wakeup = new ArrayBlockingQueue<>(1);
        private final Set<String> pending = new LinkedHashSet<>();
    }

    private final Set<Stream> streams = new LinkedHashSet<>();

    Stream subscribe() {
        if (streams.size() == MAX_STREAMS)
            return null;
        Stream stream = new Stream();
        streams.add(stream);
        return stream;
    }

    void unsubscribe(Stream stream) {
        streams.remove(stream);
    }

    void clear() {
        streams.clear();
    }

    void changed(String resource) {
        for (Stream stream : streams) {
            stream.pending.add(resource);
            // One queued wakeup covers all pending resource changes.
            // noinspection ResultOfMethodCallIgnored
            stream.wakeup.offer(Boolean.TRUE);
        }
    }

    List<String> read(Stream stream) {
        List<String> resources = new ArrayList<>(stream.pending);
        stream.pending.clear();
        return resources;
    }
}
