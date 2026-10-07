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

public final class HudPlacement {
    private final HudAnchor anchor;
    private final int offsetX;
    private final int offsetY;
    private final boolean automatic;

    public HudPlacement(HudAnchor anchor, int offsetX, int offsetY, boolean automatic) {
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.automatic = automatic;
    }

    /** The resolved screen point, including when automatic selection is enabled. */
    public HudAnchor anchor() {
        return anchor;
    }

    public int offsetX() {
        return offsetX;
    }

    public int offsetY() {
        return offsetY;
    }

    public boolean automatic() {
        return automatic;
    }

    public static HudPlacement fromOrigin(int x, int y, int elementWidth, int elementHeight,
            int screenWidth, int screenHeight) {
        HudAnchor anchor = HudAnchor.nearest(
                x == 0 ? 0 : x + elementWidth == screenWidth ? screenWidth : x + elementWidth / 2,
                y == 0 ? 0
                        : y + elementHeight == screenHeight ? screenHeight : y + elementHeight / 2,
                screenWidth, screenHeight);
        return new HudPlacement(anchor,
                x + anchor.elementX(elementWidth) - anchor.screenX(screenWidth),
                y + anchor.elementY(elementHeight) - anchor.screenY(screenHeight), true);
    }
}
