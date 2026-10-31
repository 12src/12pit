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
    private final BooleanSetting showInTab;
    private final BooleanSetting textShadow;
    private final BooleanSetting translateText;
    private final ChoiceSetting font;
    private final ChoiceSetting anchor;
    private final IntegerSetting autoAnchor;
    private final IntegerSetting offsetX;
    private final IntegerSetting offsetY;
    private final IntegerSetting scale;
    // Keep the last layout size to preserve position when the anchor changes.
    private int screenWidth;
    private int screenHeight;
    private int elementWidth;
    private int elementHeight;
    private boolean placing;

    HudConfig(BooleanSetting showInTab, BooleanSetting textShadow, BooleanSetting translateText,
            ChoiceSetting font, ChoiceSetting anchor, IntegerSetting autoAnchor,
            IntegerSetting offsetX, IntegerSetting offsetY, IntegerSetting scale) {
        this.showInTab = showInTab;
        this.textShadow = textShadow;
        this.translateText = translateText;
        this.font = font;
        this.anchor = anchor;
        this.autoAnchor = autoAnchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.scale = scale;
        anchor.beforeChange(this::changeAnchor);
    }

    public BooleanSetting showInTab() {
        return showInTab;
    }

    public BooleanSetting textShadow() {
        return textShadow;
    }

    public BooleanSetting translateText() {
        return translateText;
    }

    public ChoiceSetting font() {
        return font;
    }

    public IntegerSetting scale() {
        return scale;
    }

    public IntegerSetting autoAnchor() {
        return autoAnchor;
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
        this.screenWidth = screenWidth;
        this.elementWidth = elementWidth;
        HudAnchor currentAnchor = resolvedAnchor();
        return currentAnchor.screenX(screenWidth) - currentAnchor.elementX(elementWidth)
                + offsetX.get();
    }

    public int resolveY(int screenHeight, int elementHeight) {
        this.screenHeight = screenHeight;
        this.elementHeight = elementHeight;
        HudAnchor currentAnchor = resolvedAnchor();
        return currentAnchor.screenY(screenHeight) - currentAnchor.elementY(elementHeight)
                + offsetY.get();
    }

    public void placement(HudPlacement placement) {
        offsetX.set(placement.offsetX());
        offsetY.set(placement.offsetY());
        if (placement.automatic()) {
            autoAnchor.set(placement.anchor().id());
        }
        // Update offsets before notifying anchor listeners.
        placing = true;
        try {
            anchor.set(placement.automatic() ? AUTO_ANCHOR : placement.anchor().id());
        } finally {
            placing = false;
        }
    }

    private void changeAnchor(int value) {
        if (placing) {
            return;
        }
        HudAnchor previous = resolvedAnchor();
        HudAnchor next = HudAnchor.fromId(value == AUTO_ANCHOR ? autoAnchor.get() : value);
        if (screenWidth > 0 && screenHeight > 0) {
            // Use the visible position, including the renderer's screen bounds.
            int x = Math.max(0,
                    Math.min(Math.max(0, screenWidth - elementWidth), previous.screenX(screenWidth)
                            - previous.elementX(elementWidth) + offsetX.get()));
            int y = Math.max(0,
                    Math.min(Math.max(0, screenHeight - elementHeight),
                            previous.screenY(screenHeight) - previous.elementY(elementHeight)
                                    + offsetY.get()));
            offsetX.applyValidated(x + next.elementX(elementWidth) - next.screenX(screenWidth));
            offsetY.applyValidated(y + next.elementY(elementHeight) - next.screenY(screenHeight));
        }
    }

    private HudAnchor resolvedAnchor() {
        int value = anchor.get();
        return HudAnchor.fromId(value == AUTO_ANCHOR ? autoAnchor.get() : value);
    }
}
