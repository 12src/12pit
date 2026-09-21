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
import java.util.List;

public final class ConfigChangeSet {
    public static final class Change {
        private final String featureId;
        private final String settingId;
        private final Object previousValue;
        private final Object currentValue;

        Change(String featureId, String settingId, Object previousValue, Object currentValue) {
            this.featureId = featureId;
            this.settingId = settingId;
            this.previousValue = previousValue;
            this.currentValue = currentValue;
        }

        public String featureId() {
            return featureId;
        }

        public String settingId() {
            return settingId;
        }

        public Object previousValue() {
            return previousValue;
        }

        public Object currentValue() {
            return currentValue;
        }
    }

    private final long revision;
    private final List<Change> changes;

    ConfigChangeSet(long revision, List<Change> changes) {
        this.revision = revision;
        this.changes = Collections.unmodifiableList(new ArrayList<Change>(changes));
    }

    public long revision() {
        return revision;
    }

    public List<Change> changes() {
        return changes;
    }

    public boolean affects(String featureId, String settingId) {
        for (Change change : changes) {
            if (change.featureId.equals(featureId) && change.settingId.equals(settingId)) {
                return true;
            }
        }
        return false;
    }
}
