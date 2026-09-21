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
package pit12.feature.clickgui.component;

public final class UiAnimation {
    private static final long DURATION_NANOS = 140_000_000L;
    private float progress;
    private float start;
    private float target;
    private long startNanos;
    private boolean initialized;

    public float update(boolean active, boolean animated) {
        return update(active ? 1.0F : 0.0F, animated);
    }

    public float update(float target, boolean animated) {
        float clampedTarget = Math.max(0.0F, Math.min(1.0F, target));
        if (!animated) {
            progress = clampedTarget;
            start = clampedTarget;
            this.target = clampedTarget;
            startNanos = System.nanoTime();
            initialized = true;
            return progress;
        }
        long now = System.nanoTime();
        if (!initialized) {
            progress = clampedTarget;
            start = clampedTarget;
            this.target = clampedTarget;
            startNanos = now;
            initialized = true;
            return progress;
        }
        progress = interpolated(now);
        if (Float.compare(clampedTarget, this.target) != 0) {
            start = progress;
            this.target = clampedTarget;
            startNanos = now;
        }
        progress = interpolated(now);
        return progress;
    }

    private float interpolated(long now) {
        float elapsed = Math.min(1.0F, (float) (now - startNanos) / DURATION_NANOS);
        float remaining = 1.0F - elapsed;
        float eased = 1.0F - remaining * remaining * remaining;
        return start + (target - start) * eased;
    }
}
