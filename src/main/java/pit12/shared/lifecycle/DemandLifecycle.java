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
package pit12.shared.lifecycle;

import java.util.IdentityHashMap;

/** Each owner holds one use, compared by identity. */
public abstract class DemandLifecycle implements ClientLifecycle {
    private final Runnable checkThread;
    private final IdentityHashMap<Object, Boolean> owners = new IdentityHashMap<>(4);
    private boolean started;
    private boolean active;

    protected DemandLifecycle(Runnable checkThread) {
        this.checkThread = checkThread;
    }

    @Override
    public final void start() {
        checkThread.run();
        started = true;
        updateActivation();
    }

    @Override
    public final void stop() {
        checkThread.run();
        started = false;
        updateActivation();
        owners.clear();
    }

    /** Retain before reading live state, then release when this owner stops using it. */
    public final void retain(Object owner) {
        checkThread.run();
        retainOwner(owner);
    }

    public final void release(Object owner) {
        checkThread.run();
        releaseOwner(owner);
    }

    protected final void retainOwner(Object owner) {
        if (owners.put(owner, true) == null)
            updateActivation();
    }

    protected final void releaseOwner(Object owner) {
        if (owners.remove(owner) != null)
            updateActivation();
    }

    protected final boolean isActive() {
        return active;
    }

    private void updateActivation() {
        boolean enabled = started && !owners.isEmpty();
        if (enabled == active)
            return;
        active = enabled;
        if (active)
            activate();
        else
            deactivate();
    }

    protected abstract void activate();

    protected abstract void deactivate();
}
