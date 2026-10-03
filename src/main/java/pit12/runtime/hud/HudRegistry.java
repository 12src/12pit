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
package pit12.runtime.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pit12.shared.concurrent.ClientThread;

public final class HudRegistry {
    private final List<HudElement> elements = new ArrayList<HudElement>();
    private final List<HudElement> elementsView = Collections.unmodifiableList(elements);
    private final Map<String, HudElement> elementsById = new LinkedHashMap<String, HudElement>();
    private boolean editing;
    private final ClientThread client;

    public HudRegistry(ClientThread client) {
        this.client = client;
    }

    public void checkThread() {
        client.check();
    }

    /** Registration and reads are client-thread confined after features start. */
    public void register(HudElement element) {
        client.check();
        String id = element.id();
        HudElement existing = elementsById.get(id);
        if (existing != null && existing != element) {
            throw new IllegalArgumentException("Duplicate HUD element id: " + id);
        }
        if (existing == element) {
            return;
        }
        elements.add(element);
        elementsById.put(id, element);
    }

    public void unregister(HudElement element) {
        client.check();
        if (elementsById.get(element.id()) != element) {
            return;
        }
        elementsById.remove(element.id());
        elements.remove(element);
    }

    public List<HudElement> elements() {
        client.check();
        return elementsView;
    }

    public boolean contains(HudElement element) {
        client.check();
        return element != null && elementsById.get(element.id()) == element;
    }

    public boolean editing() {
        client.check();
        return editing;
    }

    public void setEditing(boolean editing) {
        client.check();
        this.editing = editing;
    }
}
