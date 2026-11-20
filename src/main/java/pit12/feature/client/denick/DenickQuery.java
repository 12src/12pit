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
package pit12.feature.client.denick;

import java.io.IOException;
import java.util.List;
import java.util.Map;
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
                if (query.selectOwner(api.owners(item.query, cancelled), cachedOwner,
                        cachedDisplayName)) {
                    return query;
                }
            } catch (IOException failure) {
                LOGGER.debug("Denick item query failed for {}", item.query, failure);
                query.failure = failure;
            }
        }
        return query;
    }

    boolean selectOwner(Map<String, String> owners, String cachedOwner, String cachedDisplayName) {
        Map.Entry<String, String> match = null;
        for (Map.Entry<String, String> candidate : owners.entrySet()) {
            String plain = TextCodes.strip(candidate.getValue());
            if (!plain.contains("[MVP++]") && !plain.contains("[YOUTUBE]")) {
                continue;
            }
            if (match != null) {
                return false;
            }
            match = candidate;
        }
        if (match == null) {
            return false;
        }
        owner = match.getKey();
        if (cachedDisplayName != null && owner.equals(cachedOwner)) {
            displayName = cachedDisplayName;
        } else {
            String name = match.getValue();
            int rankStart = name.indexOf('[', name.indexOf('[') + 1);
            int rankEnd = name.indexOf(']', rankStart);
            displayName = rankStart >= 0 && rankEnd >= 0
                    ? name.substring(0, rankStart) + name.substring(rankEnd + 1).trim()
                    : name;
        }
        return true;
    }
}
