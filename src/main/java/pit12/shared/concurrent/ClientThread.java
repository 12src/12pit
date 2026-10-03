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
package pit12.shared.concurrent;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Live APIs belong to this thread. Workers may only submit copied results through execute. */
public final class ClientThread {
    private final BooleanSupplier current;
    private final Consumer<Runnable> dispatch;

    public ClientThread(BooleanSupplier current, Consumer<Runnable> dispatch) {
        this.current = current;
        this.dispatch = dispatch;
    }

    public static ClientThread current() {
        Thread owner = Thread.currentThread();
        return new ClientThread(() -> Thread.currentThread() == owner, task -> {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("No client dispatcher was supplied");
            }
            task.run();
        });
    }

    public void check() {
        if (!current.getAsBoolean()) {
            throw new IllegalStateException("This API requires the client thread");
        }
    }

    public void execute(Runnable task) {
        dispatch.accept(() -> {
            check();
            task.run();
        });
    }
}
