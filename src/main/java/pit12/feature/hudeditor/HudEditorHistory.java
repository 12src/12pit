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
package pit12.feature.hudeditor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import pit12.runtime.config.HudConfig;
import pit12.runtime.config.HudPlacement;
import pit12.runtime.hud.HudElement;
import pit12.runtime.hud.HudRegistry;

final class HudEditorHistory {
    private final HudRegistry registry;
    private final Deque<Change> undo = new ArrayDeque<>();
    private final Deque<Change> redo = new ArrayDeque<>();
    private Map<HudElement, State> before;

    HudEditorHistory(HudRegistry registry) {
        this.registry = registry;
    }

    void begin(Iterable<HudElement> elements) {
        before = new LinkedHashMap<>();
        for (HudElement element : elements) {
            before.put(element, new State(element.config()));
        }
    }

    void commit() {
        Map<HudElement, State> previous = new LinkedHashMap<>();
        Map<HudElement, State> next = new LinkedHashMap<>();
        for (Map.Entry<HudElement, State> entry : before.entrySet()) {
            HudElement element = entry.getKey();
            if (!registry.contains(element)) {
                continue;
            }
            State current = new State(element.config());
            if (!entry.getValue().same(current)) {
                previous.put(element, entry.getValue());
                next.put(element, current);
            }
        }
        before = null;
        if (!previous.isEmpty()) {
            undo.addLast(new Change(previous, next));
            if (undo.size() > 100) {
                undo.removeFirst();
            }
            redo.clear();
        }
    }

    void discard() {
        before = null;
    }

    void undo() {
        if (!undo.isEmpty()) {
            Change change = undo.removeLast();
            apply(change.before);
            redo.addLast(change);
        }
    }

    void redo() {
        if (!redo.isEmpty()) {
            Change change = redo.removeLast();
            apply(change.after);
            undo.addLast(change);
        }
    }

    void clear() {
        discard();
        undo.clear();
        redo.clear();
    }

    private void apply(Map<HudElement, State> values) {
        for (Map.Entry<HudElement, State> entry : values.entrySet()) {
            if (registry.contains(entry.getKey())) {
                entry.getValue().apply(entry.getKey().config());
            }
        }
    }

    private static final class Change {
        private final Map<HudElement, State> before;
        private final Map<HudElement, State> after;

        private Change(Map<HudElement, State> before, Map<HudElement, State> after) {
            this.before = before;
            this.after = after;
        }
    }
    private static final class State {
        private final HudPlacement placement;
        private final int scale;

        private State(HudConfig config) {
            placement = config.placement();
            scale = config.scale().get();
        }

        private boolean same(State other) {
            return placement.anchor() == other.placement.anchor()
                    && placement.automatic() == other.placement.automatic()
                    && placement.offsetX() == other.placement.offsetX()
                    && placement.offsetY() == other.placement.offsetY() && scale == other.scale;
        }

        private void apply(HudConfig config) {
            config.placement(placement);
            config.scale().set(scale);
        }
    }
}
