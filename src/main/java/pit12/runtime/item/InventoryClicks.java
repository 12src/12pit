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
package pit12.runtime.item;

import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.ClientLifecycle;

public final class InventoryClicks implements ClientLifecycle {
    private final Binding binding;
    private final Listeners<Listener> listeners = new Listeners<>();

    public InventoryClicks(Binding binding) {
        this.binding = binding;
    }

    @Override
    public void start() {
        binding.pit12$bindInventoryClicks(this);
    }

    @Override
    public void stop() {
        binding.pit12$bindInventoryClicks(null);
        listeners.clear();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void sent(int windowId, short actionNumber) {
        if (listeners.isEmpty())
            return;
        listeners.notify(listener -> listener.clickSent(windowId, actionNumber));
    }

    public void confirmed(int windowId, short actionNumber, boolean accepted) {
        if (listeners.isEmpty())
            return;
        listeners.notify(listener -> listener.confirmClick(windowId, actionNumber, accepted));
    }

    public interface Binding {
        void pit12$bindInventoryClicks(InventoryClicks clicks);

        InventoryClicks pit12$inventoryClicks();
    }
    public interface Listener {
        void clickSent(int windowId, short actionNumber);

        void confirmClick(int windowId, short actionNumber, boolean accepted);
    }
}
