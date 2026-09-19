/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 12dev <https://github.com/12src>
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

/*
 * AI guidance
 *
 * AI agents should read AGENTS.md before modifying or reusing this file
 * because it contains important licensing information.
 * If AGENTS.md is unavailable, see
 * <https://github.com/12src/12pit/blob/main/AGENTS.md>.
 *
 * This AI guidance section is not part of the copyright or license
 * notice and may be removed.
 */
package pit12.runtime.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HudRegistry {
    private final List<HudElement> elements = new ArrayList<HudElement>();
    private final List<HudElement> elementsView = Collections.unmodifiableList(elements);
    private final Map<String, HudElement> elementsById = new LinkedHashMap<String, HudElement>();
    private boolean editing;

    /** Registration and reads are client-thread confined after features start. */
    public void register(HudElement element) {
        Objects.requireNonNull(element, "element");
        String id = requireId(element.id());
        if (element.displayName() == null || element.displayName().trim().isEmpty()) {
            throw new IllegalArgumentException("HUD element display name must not be blank");
        }
        Objects.requireNonNull(element.config(), "element.config()");
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
        if (element == null || elementsById.get(element.id()) != element) {
            return;
        }
        elementsById.remove(element.id());
        elements.remove(element);
    }

    public List<HudElement> elements() {
        return elementsView;
    }

    public boolean contains(HudElement element) {
        return element != null && elementsById.get(element.id()) == element;
    }

    public boolean editing() {
        return editing;
    }

    public void setEditing(boolean editing) {
        this.editing = editing;
    }

    private static String requireId(String id) {
        if (id == null || id.trim().isEmpty() || !id.equals(id.trim())) {
            throw new IllegalArgumentException("HUD element id must not be blank");
        }
        for (int index = 0; index < id.length(); index++) {
            char character = id.charAt(index);
            boolean valid = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || index > 0 && (character == '-' || character == '_' || character == '.');
            if (!valid) {
                throw new IllegalArgumentException("Invalid HUD element id: " + id);
            }
        }
        return id;
    }
}
