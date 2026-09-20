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

public abstract class Setting<T> {
    public enum StorageType {
        BOOLEAN, INTEGER, DECIMAL
    }
    interface ChangeSink {
        void changed(Setting<?> setting, Object previousValue, Object currentValue);
    }

    private final String id;
    private final String displayName;
    private final String description;
    private final T defaultValue;
    private final StorageType storageType;
    private T value;
    private ChangeSink changeSink;

    /**
     * Subclasses validate their default after initializing any state used by {@link #requireValue(Object)}.
     */
    protected Setting(String id, String displayName, String description, T defaultValue,
            StorageType storageType) {
        this.id = ConfigNames.requireStableId(id, "setting id");
        this.displayName = ConfigNames.requireText(displayName, "setting display name");
        this.description = ConfigNames.requireDescription(description);
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.storageType = Objects.requireNonNull(storageType, "storageType");
        value = this.defaultValue;
    }

    public final String id() {
        return id;
    }

    public final String displayName() {
        return displayName;
    }

    public final String description() {
        return description;
    }

    public final T defaultValue() {
        return defaultValue;
    }

    public final StorageType storageType() {
        return storageType;
    }

    public final T get() {
        return value;
    }

    /** Setting values are client-thread confined once their catalog is used by started features. */
    public final void set(T candidate) {
        T validated = requireValue(candidate);
        if (Objects.equals(value, validated)) {
            return;
        }
        T previous = value;
        value = validated;
        if (changeSink != null) {
            changeSink.changed(this, previous, validated);
        }
    }

    protected abstract T requireValue(Object candidate);

    final Object validatedCandidate(Object candidate) {
        return requireValue(candidate);
    }

    @SuppressWarnings("unchecked")
    final void applyValidated(Object candidate) {
        value = (T) candidate;
    }

    final void bind(ChangeSink sink) {
        if (changeSink != null) {
            throw new IllegalStateException("Setting is already registered: " + id);
        }
        changeSink = Objects.requireNonNull(sink, "sink");
    }
}
