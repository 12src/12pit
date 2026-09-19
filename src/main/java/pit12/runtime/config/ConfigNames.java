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
package pit12.runtime.config;

final class ConfigNames {
    private ConfigNames() {}

    static String requireStableId(String value, String label) {
        String id = requireText(value, label);
        for (int index = 0; index < id.length(); index++) {
            char character = id.charAt(index);
            boolean valid = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || index > 0 && (character == '-' || character == '_' || character == '.');
            if (!valid) {
                throw new IllegalArgumentException(label
                        + " must use lowercase ASCII letters, digits, '.', '-' or '_': " + value);
            }
        }
        return id;
    }

    static String requireText(String value, String label) {
        if (value == null) {
            throw new NullPointerException(label);
        }
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return text;
    }

    static String requireDescription(String value) {
        if (value == null) {
            throw new NullPointerException("description");
        }
        return value.trim();
    }
}
