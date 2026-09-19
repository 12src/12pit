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
package pit12.runtime.config;

import java.util.Objects;

public final class HudConfig {
    private final String id;
    private final String displayName;
    private final BooleanSetting textShadow;
    private final IntegerSetting anchor;
    private final IntegerSetting offsetX;
    private final IntegerSetting offsetY;
    private final IntegerSetting scale;

    HudConfig(String id, String displayName, BooleanSetting textShadow, IntegerSetting anchor,
            IntegerSetting offsetX, IntegerSetting offsetY, IntegerSetting scale) {
        this.id = id;
        this.displayName = displayName;
        this.textShadow = textShadow;
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.scale = scale;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public BooleanSetting textShadow() {
        return textShadow;
    }

    public IntegerSetting scale() {
        return scale;
    }

    public float scaleFactor() {
        return scale.get().intValue() / 100.0F;
    }

    public int resolveX(int screenWidth, int elementWidth) {
        HudAnchor currentAnchor = HudAnchor.fromId(anchor.get().intValue());
        return currentAnchor.screenX(screenWidth) - currentAnchor.elementX(elementWidth)
                + offsetX.get().intValue();
    }

    public int resolveY(int screenHeight, int elementHeight) {
        HudAnchor currentAnchor = HudAnchor.fromId(anchor.get().intValue());
        return currentAnchor.screenY(screenHeight) - currentAnchor.elementY(elementHeight)
                + offsetY.get().intValue();
    }

    public void placement(HudPlacement placement) {
        Objects.requireNonNull(placement, "placement");
        offsetX.set(Integer.valueOf(placement.offsetX()));
        offsetY.set(Integer.valueOf(placement.offsetY()));
        // An anchor-change listener must not observe offsets from the previous placement.
        anchor.set(Integer.valueOf(placement.anchor().id()));
    }
}
