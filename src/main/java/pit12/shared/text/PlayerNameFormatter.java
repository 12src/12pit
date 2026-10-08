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
package pit12.shared.text;

public final class PlayerNameFormatter {
    private PlayerNameFormatter() {}

    public static String shorten(String name) {
        int start = name.indexOf('[');
        if (start < 0) {
            // Megastreak names have no level bracket.
            return name;
        }
        int firstSpace = name.indexOf(' ', start);
        int end = firstSpace < 0 ? name.length() : name.indexOf(' ', firstSpace + 1);
        if (end < 0) {
            end = name.length();
        }
        StringBuilder shortened = new StringBuilder(end);
        for (int index = 0; index < start; index++) {
            if (name.charAt(index) == '\u00A7' && index + 1 < start) {
                shortened.append(name, index, index + 2);
                index++;
            }
        }
        return shortened.append(name, start, end).toString();
    }
}
