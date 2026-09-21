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
package pit12.runtime.config;

public enum HudAnchor {
    TOP_LEFT(0, 0, 0),
    TOP_CENTER(1, 1, 0),
    TOP_RIGHT(2, 2, 0),
    CENTER_LEFT(3, 0, 1),
    CENTER(4, 1, 1),
    CENTER_RIGHT(5, 2, 1),
    BOTTOM_LEFT(6, 0, 2),
    BOTTOM_CENTER(7, 1, 2),
    BOTTOM_RIGHT(8, 2, 2);

    private static final HudAnchor[] BY_ID = new HudAnchor[9];
    static {
        for (HudAnchor anchor : values()) {
            if (anchor.id < 0 || anchor.id >= BY_ID.length || BY_ID[anchor.id] != null) {
                throw new IllegalStateException("Invalid HUD anchor id: " + anchor.id);
            }
            BY_ID[anchor.id] = anchor;
        }
    }
    private final int id;
    private final int horizontal;
    private final int vertical;

    HudAnchor(int id, int horizontal, int vertical) {
        this.id = id;
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public int id() {
        return id;
    }

    public int screenX(int screenWidth) {
        return coordinate(screenWidth, horizontal);
    }

    public int screenY(int screenHeight) {
        return coordinate(screenHeight, vertical);
    }

    public int elementX(int elementWidth) {
        return coordinate(elementWidth, horizontal);
    }

    public int elementY(int elementHeight) {
        return coordinate(elementHeight, vertical);
    }

    public static HudAnchor fromId(int id) {
        if (id < 0 || id >= BY_ID.length || BY_ID[id] == null) {
            throw new IllegalArgumentException("Unknown HUD anchor: " + id);
        }
        return BY_ID[id];
    }

    public static HudAnchor nearest(int centerX, int centerY, int screenWidth, int screenHeight) {
        int horizontal = region(centerX, screenWidth);
        int vertical = region(centerY, screenHeight);
        return BY_ID[vertical * 3 + horizontal];
    }

    private static int coordinate(int length, int alignment) {
        return alignment == 0 ? 0 : alignment == 1 ? length / 2 : length;
    }

    private static int region(int coordinate, int length) {
        if (coordinate * 3 < length) {
            return 0;
        }
        return coordinate * 3 > length * 2 ? 2 : 1;
    }
}
