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

import java.util.ArrayList;
import java.util.List;
import pit12.runtime.hud.HudBounds;

final class HudEditorSnap {
    final Axis horizontal = new Axis();
    final Axis vertical = new Axis();

    void prepare(Box moving, int screenWidth, int screenHeight, Iterable<HudBounds> others) {
        horizontal.prepare(moving.width, screenWidth);
        vertical.prepare(moving.height, screenHeight);
        for (HudBounds other : others) {
            horizontal.addElement(moving.width, other.x, other.width, other.y,
                    other.y + other.height);
            vertical.addElement(moving.height, other.y, other.height, other.x,
                    other.x + other.width);
        }
    }

    void release() {
        horizontal.active = null;
        vertical.active = null;
    }

    static final class Box {
        final int x;
        final int y;
        final int width;
        final int height;

        Box(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
    static final class Target {
        final int origin;
        final int line;
        final int start;
        final int end;
        final boolean gap;

        private Target(int origin, int line, int start, int end, boolean gap) {
            this.origin = origin;
            this.line = line;
            this.start = start;
            this.end = end;
            this.gap = gap;
        }
    }
    static final class Axis {
        private final List<Target> targets = new ArrayList<>();
        private int maximum;
        Target active;

        private void prepare(int length, int screenLength) {
            targets.clear();
            active = null;
            maximum = Math.max(0, screenLength - length);
            add(0, 0, 0, 0, false);
            add(screenLength - length, screenLength, 0, 0, false);
            add(screenLength / 2 - length / 2, screenLength / 2, 0, 0, false);
        }

        private void addElement(int length, int origin, int otherLength, int start, int end) {
            for (int target : new int[] {origin, origin + otherLength / 2, origin + otherLength}) {
                for (int offset : new int[] {0, length / 2, length}) {
                    add(target - offset, target, start, end, false);
                }
            }
            add(origin - 6 - length, origin, start, end, true);
            add(origin + otherLength + 6, origin + otherLength, start, end, true);
        }

        private void add(int origin, int line, int start, int end, boolean gap) {
            if (origin >= 0 && origin <= maximum) {
                targets.add(new Target(origin, line, start, end, gap));
            }
        }

        int resolve(int value, boolean disabled) {
            value = Math.max(0, Math.min(maximum, value));
            if (disabled) {
                active = null;
                return value;
            }
            // Reaching a screen edge must break the previous snap.
            if (active != null && value > 0 && value < maximum
                    && Math.abs(value - active.origin) <= 10) {
                return active.origin;
            }
            active = null;
            int nearest = 7;
            for (Target target : targets) {
                int distance = Math.abs(value - target.origin);
                if (distance < nearest) {
                    nearest = distance;
                    active = target;
                }
            }
            return active == null ? value : active.origin;
        }
    }
}
