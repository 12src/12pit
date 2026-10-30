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
package pit12.feature.misc.denick;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pit12.shared.text.TextCodes;

final class DenickQuery {
    private static final Logger LOGGER = LogManager.getLogger(DenickQuery.class);
    String owner;
    String displayName;
    Exception failure;

    static DenickQuery run(PitPalClient api, List<DenickItem> items, String cachedOwner,
            String cachedDisplayName, BooleanSupplier cancelled) throws InterruptedException {
        DenickQuery query = new DenickQuery();
        for (DenickItem item : items) {
            if (cancelled.getAsBoolean()) {
                throw new InterruptedException("Denick query was cancelled");
            }
            try {
                Map<String, String> owners = api.owners(item.query, cancelled);
                owners.values().removeIf(displayName -> {
                    String plain = TextCodes.strip(displayName);
                    return !plain.contains("[MVP++]") && !plain.contains("[YOUTUBE]");
                });
                if (owners.size() != 1) {
                    continue;
                }
                Map.Entry<String, String> owner = owners.entrySet().iterator().next();
                query.owner = owner.getKey();
                String displayName = owner.getValue();
                int rankStart = displayName.indexOf('[', displayName.indexOf('[') + 1);
                int rankEnd = displayName.indexOf(']', rankStart);
                if (rankStart >= 0 && rankEnd >= 0) {
                    displayName = displayName.substring(0, rankStart)
                            + displayName.substring(rankEnd + 1).trim();
                }
                query.displayName =
                        Objects.equals(cachedOwner, owner.getKey()) && cachedDisplayName != null
                                ? cachedDisplayName
                                : displayName;
                return query;
            } catch (IOException failure) {
                LOGGER.debug("Denick item query failed for {}", item.query, failure);
                query.failure = failure;
            }
        }
        return query;
    }
}
