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

final class HudEditorSnap {
    final Axis horizontal = new Axis();
    final Axis vertical = new Axis();

    void prepare(Box moving, int screenWidth, int screenHeight, Iterable<Box> others) {
        horizontal.prepare(moving.width, screenWidth);
        vertical.prepare(moving.height, screenHeight);
        for (Box other : others) {
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
        final int priority;

        private Target(int origin, int line, int start, int end, int priority) {
            this.origin = origin;
            this.line = line;
            this.start = start;
            this.end = end;
            this.priority = priority;
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
            add(0, 0, 0, 0, 3);
            add(screenLength - length, screenLength, 0, 0, 3);
            add(screenLength / 2 - length / 2, screenLength / 2, 0, 0, 0);
            add(screenLength / 2, screenLength / 2, 0, 0, 0);
            add(screenLength / 2 - length, screenLength / 2, 0, 0, 0);
        }

        private void addElement(int length, int origin, int otherLength, int start, int end) {
            for (int target = 0; target < 3; target++) {
                int line = origin + (target == 0 ? 0 : target == 1 ? otherLength / 2 : otherLength);
                for (int alignment = 0; alignment < 3; alignment++) {
                    int offset = alignment == 0 ? 0 : alignment == 1 ? length / 2 : length;
                    add(line - offset, line, start, end, target == alignment ? 2 : 1);
                }
            }
        }

        private void add(int origin, int line, int start, int end, int priority) {
            if (origin >= 0 && origin <= maximum) {
                targets.add(new Target(origin, line, start, end, priority));
            }
        }

        int resolve(int value, boolean disabled) {
            value = Math.max(0, Math.min(maximum, value));
            if (disabled) {
                active = null;
                return value;
            }
            // Let go of the old snap when we hit the screen edge.
            if (active != null && value > 0 && value < maximum
                    && Math.abs(value - active.origin) <= 4) {
                return active.origin;
            }
            active = null;
            int nearest = 3;
            for (Target target : targets) {
                int distance = Math.abs(value - target.origin);
                // At the same distance, prefer screen edges, then matching HUD sides.
                if (distance < nearest || active != null && distance == nearest
                        && target.priority > active.priority) {
                    nearest = distance;
                    active = target;
                }
            }
            return active == null ? value : active.origin;
        }

        int alignment(int origin, int length, int fallback) {
            if (active != null) {
                if (active.line == origin) {
                    return 0;
                }
                if (active.line == origin + length) {
                    return 2;
                }
                if (active.line == origin + length / 2) {
                    return 1;
                }
            }
            return fallback;
        }
    }
}
