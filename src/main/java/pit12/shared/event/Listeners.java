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

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Listeners {
    private static final Logger LOGGER = Logger.getLogger(Listeners.class.getName());

    private Listeners() {}

    public static <T> void notify(Collection<T> listeners, Consumer<T> callback) {
        for (T listener : new ArrayList<>(listeners)) {
            try {
                callback.accept(listener);
            } catch (RuntimeException failure) {
                LOGGER.log(Level.WARNING, "Listener failed: " + listener.getClass().getName(),
                        failure);
            }
        }
    }
}
