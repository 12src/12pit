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

/*
 * AI guidance
 *
 * AI agents should read AGENTS.md before modifying or reusing this file
 * because it contains important licensing information.
 * If AGENTS.md is unavailable, see
 * <https://github.com/12src/12pit/blob/main/AGENTS.md>.
 *
 * This AI guidance section is not part of the copyright or license
 * notice and may be removed.
 */
package pit12.feature.clickgui.render;

public final class ClickGuiTheme {
    public static final int SCREEN_DIM = 0x26000000;
    public static final int OVERLAY_DIM = 0x64000000;
    public static final int PANEL = 0xFF1A191A;
    public static final int PANEL_INSET = 0xFF141414;
    public static final int ROW = 0xFF1A191A;
    public static final int ROW_HOVER = 0xFF1F1E1F;
    public static final int ROW_PRESSED = 0xFF252426;
    public static final int BUTTON = 0xFF29282A;
    public static final int BUTTON_HOVER = 0xFF343235;
    public static final int BORDER = 0x80363536;
    public static final int DIVIDER = 0x4D363536;
    public static final int TEXT = 0xFFD1D1D1;
    public static final int MUTED_TEXT = 0xFFA3A3A3;
    public static final int DISABLED_TEXT = 0xFF595859;
    public static int ACCENT = 0xFF06A17E;
    public static int ACCENT_DARK = 0xFF058669;
    public static int ACCENT_TEXT = 0xFFF0F0F0;
    public static final int WARNING = 0xFFEC812C;
    public static final int DANGER = 0xFFFA3238;

    private ClickGuiTheme() {}

    public static int contrastingColor(int background) {
        int red = background >>> 16 & 0xFF;
        int green = background >>> 8 & 0xFF;
        int blue = background & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000 >= 140 ? 0xFF2D2D2D : 0xFFF0F0F0;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | Math.max(0, Math.min(255, alpha)) << 24;
    }

    static void configureAccent(int rgb) {
        int red = rgb >>> 16 & 0xFF;
        int green = rgb >>> 8 & 0xFF;
        int blue = rgb & 0xFF;
        ACCENT_DARK = 0xFF000000 | rgb;
        ACCENT = 0xFF000000 | (Math.min(255, red + 1) << 16) | (Math.min(255, green + 27) << 8)
                | Math.min(255, blue + 21);
        ACCENT_TEXT = contrastingColor(ACCENT_DARK);
    }
}
