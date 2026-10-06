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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import pit12.runtime.languages.Languages;

public final class ChoiceSetting extends Setting<Integer> {
    public static final class Choice {
        private final int value;
        private final String originalDisplayName;
        private String displayName;

        public Choice(int value, String displayName) {
            this.value = value;
            originalDisplayName = displayName;
            this.displayName = displayName;
        }

        public int value() {
            return value;
        }

        public String displayName() {
            return displayName;
        }

        public String originalDisplayName() {
            return originalDisplayName;
        }
    }

    private final List<Choice> choices;
    private final Set<Integer> values = new HashSet<Integer>();

    ChoiceSetting(String id, String displayName, String description, int defaultValue,
            Choice... choices) {
        super(id, displayName, description, defaultValue, StorageType.INTEGER);
        for (Choice choice : choices) {
            if (!values.add(choice.value())) {
                throw new IllegalArgumentException("Duplicate choice value " + choice.value());
            }
        }
        this.choices = Arrays.asList(choices);
        requireValue(defaultValue);
    }

    @Override
    void localize(Languages language) {
        super.localize(language);
        for (Choice choice : choices) {
            choice.displayName = language.translate(choice.originalDisplayName);
        }
    }

    public List<Choice> choices() {
        return choices;
    }

    @Override
    protected Integer requireValue(Object candidate) {
        if (!(candidate instanceof Number)) {
            throw new IllegalArgumentException("Setting " + id() + " requires an integer choice");
        }
        Number number = (Number) candidate;
        int value = number.intValue();
        if (number.doubleValue() != value || !values.contains(value)) {
            throw new IllegalArgumentException(
                    "Setting " + id() + " requires one of its configured choices");
        }
        return value;
    }
}
