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
package pit12.shared.rendering;

final class McFormatting {
    private McFormatting() {}

    static int color(char code, int baseColor, int currentColor) {
        int red;
        int green;
        int blue;
        switch (Character.toLowerCase(code)) {
            case '0':
                red = 0;
                green = 0;
                blue = 0;
                break;
            case '1':
                red = 0;
                green = 0;
                blue = 170;
                break;
            case '2':
                red = 0;
                green = 170;
                blue = 0;
                break;
            case '3':
                red = 0;
                green = 170;
                blue = 170;
                break;
            case '4':
                red = 170;
                green = 0;
                blue = 0;
                break;
            case '5':
                red = 170;
                green = 0;
                blue = 170;
                break;
            case '6':
                red = 255;
                green = 170;
                blue = 0;
                break;
            case '7':
                red = 170;
                green = 170;
                blue = 170;
                break;
            case '8':
                red = 85;
                green = 85;
                blue = 85;
                break;
            case '9':
                red = 85;
                green = 85;
                blue = 255;
                break;
            case 'a':
                red = 85;
                green = 255;
                blue = 85;
                break;
            case 'b':
                red = 85;
                green = 255;
                blue = 255;
                break;
            case 'c':
                red = 255;
                green = 85;
                blue = 85;
                break;
            case 'd':
                red = 255;
                green = 85;
                blue = 255;
                break;
            case 'e':
                red = 255;
                green = 255;
                blue = 85;
                break;
            case 'f':
                red = 255;
                green = 255;
                blue = 255;
                break;
            case 'r':
                return baseColor;
            default:
                return currentColor;
        }
        return baseColor & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static int color(char code, int baseColor, int currentColor, boolean shadow) {
        int result = color(code, baseColor, currentColor);
        return shadow && isColor(code) ? shadowColor(result) : result;
    }

    static boolean isBold(char code) {
        return Character.toLowerCase(code) == 'l';
    }

    static boolean resetsStyle(char code) {
        char normalized = Character.toLowerCase(code);
        return normalized == 'r' || isColor(normalized);
    }

    static boolean isColor(char code) {
        return code >= '0' && code <= '9' || code >= 'a' && code <= 'f';
    }

    static int shadowColor(int color) {
        return color & 0xFF000000 | (color & 0x00FCFCFC) >> 2;
    }
}
