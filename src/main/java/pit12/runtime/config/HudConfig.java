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

public final class HudConfig {
    static final int AUTO_ANCHOR = 9;
    private final BooleanSetting textShadow;
    private final BooleanSetting useMonospaceFont;
    private final BooleanSetting translateText;
    private final ChoiceSetting anchor;
    private final IntegerSetting autoAnchor;
    private final IntegerSetting offsetX;
    private final IntegerSetting offsetY;
    private final IntegerSetting scale;

    HudConfig(BooleanSetting textShadow, BooleanSetting useMonospaceFont,
            BooleanSetting translateText, ChoiceSetting anchor, IntegerSetting autoAnchor,
            IntegerSetting offsetX, IntegerSetting offsetY, IntegerSetting scale) {
        this.textShadow = textShadow;
        this.useMonospaceFont = useMonospaceFont;
        this.translateText = translateText;
        this.anchor = anchor;
        this.autoAnchor = autoAnchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.scale = scale;
    }

    public BooleanSetting textShadow() {
        return textShadow;
    }

    public BooleanSetting useMonospaceFont() {
        return useMonospaceFont;
    }

    public BooleanSetting translateText() {
        return translateText;
    }

    public IntegerSetting scale() {
        return scale;
    }

    public float scaleFactor() {
        return scale.get() / 100.0F;
    }

    public HudPlacement placement() {
        return new HudPlacement(resolvedAnchor(), offsetX.get(), offsetY.get(),
                anchor.get() == AUTO_ANCHOR);
    }

    public HudPlacement defaultPlacement() {
        return new HudPlacement(HudAnchor.fromId(autoAnchor.defaultValue()), offsetX.defaultValue(),
                offsetY.defaultValue(), true);
    }

    public int resolveX(int screenWidth, int elementWidth) {
        HudAnchor currentAnchor = resolvedAnchor();
        return currentAnchor.screenX(screenWidth) - currentAnchor.elementX(elementWidth)
                + offsetX.get();
    }

    public int resolveY(int screenHeight, int elementHeight) {
        HudAnchor currentAnchor = resolvedAnchor();
        return currentAnchor.screenY(screenHeight) - currentAnchor.elementY(elementHeight)
                + offsetY.get();
    }

    public void placement(HudPlacement placement) {
        offsetX.set(placement.offsetX());
        offsetY.set(placement.offsetY());
        autoAnchor.set(placement.anchor().id());
        // Set the offsets and resolved anchor before changing the anchor mode.
        anchor.set(placement.automatic() ? AUTO_ANCHOR : placement.anchor().id());
    }

    private HudAnchor resolvedAnchor() {
        int value = anchor.get();
        return HudAnchor.fromId(value == AUTO_ANCHOR ? autoAnchor.get() : value);
    }
}
