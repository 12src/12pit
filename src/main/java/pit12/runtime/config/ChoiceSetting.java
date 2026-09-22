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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChoiceSetting extends Setting<Integer> {
    public static final class Choice {
        private final int value;
        private final String displayName;

        public Choice(int value, String displayName) {
            this.value = value;
            this.displayName = ConfigNames.requireText(displayName, "choice display name");
        }

        public int value() {
            return value;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final List<Choice> choices;
    private final Map<Integer, Choice> choicesByValue;

    ChoiceSetting(String id, String displayName, String description, int defaultValue,
            Choice... choices) {
        super(id, displayName, description, Integer.valueOf(defaultValue), StorageType.INTEGER);
        if (choices == null || choices.length == 0) {
            throw new IllegalArgumentException("Choice setting requires at least one choice");
        }
        ArrayList<Choice> ordered = new ArrayList<Choice>(choices.length);
        LinkedHashMap<Integer, Choice> indexed = new LinkedHashMap<Integer, Choice>();
        for (Choice choice : choices) {
            if (choice == null) {
                throw new NullPointerException("choice");
            }
            Integer value = Integer.valueOf(choice.value());
            if (indexed.put(value, choice) != null) {
                throw new IllegalArgumentException("Duplicate choice value " + value);
            }
            ordered.add(choice);
        }
        this.choices = Collections.unmodifiableList(ordered);
        choicesByValue = Collections.unmodifiableMap(indexed);
        requireValue(Integer.valueOf(defaultValue));
    }

    public List<Choice> choices() {
        return choices;
    }

    public Choice selectedChoice() {
        return choicesByValue.get(get());
    }

    @Override
    protected Integer requireValue(Object candidate) {
        if (!(candidate instanceof Number)) {
            throw new IllegalArgumentException("Setting " + id() + " requires an integer choice");
        }
        Number number = (Number) candidate;
        int value = number.intValue();
        if (number.doubleValue() != value || !choicesByValue.containsKey(Integer.valueOf(value))) {
            throw new IllegalArgumentException(
                    "Setting " + id() + " requires one of its configured choices");
        }
        return Integer.valueOf(value);
    }
}
