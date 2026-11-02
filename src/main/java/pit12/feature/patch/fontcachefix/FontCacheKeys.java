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
package pit12.feature.patch.fontcachefix;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public final class FontCacheKeys {
    private final String prefix;
    private final Cache<String, String> regular =
            CacheBuilder.newBuilder().maximumSize(2048).build();
    private final Cache<String, String> unicode =
            CacheBuilder.newBuilder().maximumSize(2048).build();

    public FontCacheKeys(String prefix) {
        this.prefix = prefix;
    }

    public String key(String text, boolean unicode) {
        Cache<String, String> keys = unicode ? this.unicode : regular;
        String key = keys.getIfPresent(text);
        if (key == null) {
            key = prefix + (unicode ? "\u0001" : "\u0000") + text;
            keys.put(text, key);
        }
        return key;
    }
}
