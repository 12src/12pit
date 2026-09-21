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

import java.util.Objects;

public final class HudPlacement {
    private final HudAnchor anchor;
    private final int offsetX;
    private final int offsetY;

    public HudPlacement(HudAnchor anchor, int offsetX, int offsetY) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public HudAnchor anchor() {
        return anchor;
    }

    public int offsetX() {
        return offsetX;
    }

    public int offsetY() {
        return offsetY;
    }

    public int resolveX(int screenWidth, int elementWidth) {
        return anchor.screenX(screenWidth) - anchor.elementX(elementWidth) + offsetX;
    }

    public int resolveY(int screenHeight, int elementHeight) {
        return anchor.screenY(screenHeight) - anchor.elementY(elementHeight) + offsetY;
    }

    public static HudPlacement fromOrigin(int x, int y, int elementWidth, int elementHeight,
            int screenWidth, int screenHeight) {
        HudAnchor anchor = HudAnchor.nearest(x + elementWidth / 2, y + elementHeight / 2,
                screenWidth, screenHeight);
        return new HudPlacement(anchor,
                x + anchor.elementX(elementWidth) - anchor.screenX(screenWidth),
                y + anchor.elementY(elementHeight) - anchor.screenY(screenHeight));
    }
}
