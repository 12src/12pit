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
package pit12.shared.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public final class ListenersTest {
    @Test
    public void aRepeatedSubscriptionReceivesOneNotification() {
        Listeners<Runnable> listeners = new Listeners<>();
        List<String> calls = new ArrayList<>();
        Runnable listener = () -> calls.add("called");
        assertTrue(listeners.add(listener));
        assertFalse(listeners.add(listener));
        listeners.notify(Runnable::run);
        assertEquals(Collections.singletonList("called"), calls);
        listeners.remove(listener);
        listeners.notify(Runnable::run);
        assertEquals(Collections.singletonList("called"), calls);
    }

    @Test
    public void subscriptionChangesTakeEffectOnTheNextNotification() {
        Listeners<Runnable> listeners = new Listeners<>();
        List<String> calls = new ArrayList<>();
        Runnable second = () -> calls.add("second");
        Runnable added = () -> calls.add("added");
        Runnable first = new Runnable() {
            @Override
            public void run() {
                calls.add("first");
                listeners.remove(this);
                listeners.remove(second);
                listeners.add(added);
            }
        };
        listeners.add(first);
        listeners.add(second);
        listeners.notify(Runnable::run);
        assertEquals(2, calls.size());
        assertEquals(new HashSet<>(Arrays.asList("first", "second")), new HashSet<>(calls));
        calls.clear();
        listeners.notify(Runnable::run);
        assertEquals(Collections.singletonList("added"), calls);
    }

    @Test
    public void clearingSubscriptionsStopsFutureNotifications() {
        Listeners<Runnable> listeners = new Listeners<>();
        List<String> calls = new ArrayList<>();
        listeners.add(() -> calls.add("first"));
        listeners.add(() -> calls.add("second"));
        listeners.clear();
        listeners.notify(Runnable::run);
        assertTrue(listeners.isEmpty());
        assertTrue(calls.isEmpty());
    }

    @Test
    public void aFailingListenerLeavesOtherListenersRunning() {
        Listeners<Runnable> listeners = new Listeners<>();
        List<String> calls = new ArrayList<>();
        listeners.add(() -> {
            throw new IllegalStateException("Listener failed");
        });
        listeners.add(() -> calls.add("called"));
        listeners.notify(Runnable::run);
        assertEquals(Collections.singletonList("called"), calls);
    }
}
