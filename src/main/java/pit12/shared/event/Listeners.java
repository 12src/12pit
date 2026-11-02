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

import java.util.Arrays;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Listeners<T> {
    private static final Logger LOGGER = LogManager.getLogger(Listeners.class);
    private static final Object[] EMPTY = new Object[0];
    private Object[] listeners = EMPTY;

    public boolean add(T listener) {
        for (Object existing : listeners) {
            if (listener.equals(existing))
                return false;
        }
        Object[] updated = Arrays.copyOf(listeners, listeners.length + 1);
        updated[listeners.length] = listener;
        listeners = updated;
        return true;
    }

    public void remove(T listener) {
        for (int index = 0; index < listeners.length; index++) {
            if (!listener.equals(listeners[index]))
                continue;
            Object[] updated = new Object[listeners.length - 1];
            System.arraycopy(listeners, 0, updated, 0, index);
            System.arraycopy(listeners, index + 1, updated, index, listeners.length - index - 1);
            listeners = updated;
            return;
        }
    }

    public boolean isEmpty() {
        return listeners.length == 0;
    }

    public void clear() {
        listeners = EMPTY;
    }

    public void notify(Consumer<T> callback) {
        // Callbacks can change subscriptions while this notification is running.
        Object[] snapshot = listeners;
        for (Object value : snapshot) {
            @SuppressWarnings("unchecked")
            T listener = (T) value;
            try {
                callback.accept(listener);
            } catch (RuntimeException failure) {
                LOGGER.error("Listener failed: {}", listener.getClass().getName(), failure);
            }
        }
    }
}
