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

import java.util.Objects;

public final class ConfigCategory implements Comparable<ConfigCategory> {
    private final String id;
    private final String displayName;
    private final int displayOrder;

    public ConfigCategory(String id, String displayName, int displayOrder) {
        this.id = ConfigNames.requireStableId(id, "category id");
        this.displayName = ConfigNames.requireText(displayName, "category display name");
        this.displayOrder = displayOrder;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int displayOrder() {
        return displayOrder;
    }

    @Override
    public int compareTo(ConfigCategory other) {
        int orderComparison = Integer.compare(displayOrder, other.displayOrder);
        return orderComparison != 0 ? orderComparison : id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigCategory)) {
            return false;
        }
        ConfigCategory category = (ConfigCategory) other;
        return id.equals(category.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
