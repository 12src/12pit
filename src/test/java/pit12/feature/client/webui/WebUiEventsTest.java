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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class WebUiEventsTest {
    @Test
    public void coalescesPendingChangesIntoASingleWakeupUntilRead() {
        WebUiEvents events = new WebUiEvents();
        WebUiEvents.Stream stream = events.subscribe();
        assertNotNull(stream);
        events.changed("profiles");
        events.changed("relations");
        events.changed("profiles");
        assertEquals(1, stream.wakeup.size());
        assertEquals(Boolean.TRUE, stream.wakeup.poll());
        assertEquals(Arrays.asList("profiles", "relations"), events.read(stream));
        assertTrue(events.read(stream).isEmpty());
    }

    @Test
    public void capsConcurrentStreamsUntilOneUnsubscribes() {
        WebUiEvents events = new WebUiEvents();
        List<WebUiEvents.Stream> active = new ArrayList<>();
        for (int index = 0; index < WebUiEvents.MAX_STREAMS; index++) {
            WebUiEvents.Stream stream = events.subscribe();
            assertNotNull(stream);
            active.add(stream);
        }
        assertNull(events.subscribe());
        events.unsubscribe(active.get(0));
        assertNotNull(events.subscribe());
    }
}
