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
package pit12.runtime.item;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class PitEnchantments implements Iterable<PitEnchantments.Entry> {
    private static final PitEnchantments EMPTY = new PitEnchantments(new Entry[0]);
    private final Entry[] entries;

    private PitEnchantments(Entry[] entries) {
        this.entries = entries;
    }

    static PitEnchantments create(Entry[] entries, int size) {
        if (size == 0) {
            return EMPTY;
        }
        // The reader does not retain the array, so storing it directly does not expose mutable state.
        return new PitEnchantments(size == entries.length ? entries : Arrays.copyOf(entries, size));
    }

    public static PitEnchantments empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return entries.length == 0;
    }

    public int size() {
        return entries.length;
    }

    public Entry get(int index) {
        return entries[index];
    }

    // Enchanted items normally have at most three entries, so linear scans remain cheap.
    public boolean contains(PitEnchantment enchantment) {
        return levelOf(enchantment) > 0;
    }

    public int levelOf(PitEnchantment enchantment) {
        if (enchantment == null) {
            return 0;
        }
        for (Entry entry : entries) {
            if (entry.enchantment == enchantment) {
                return entry.level;
            }
        }
        return 0;
    }

    public boolean containsKey(String key) {
        return levelOfKey(key) > 0;
    }

    public int levelOfKey(String key) {
        if (key == null) {
            return 0;
        }
        for (Entry entry : entries) {
            if (key.equals(entry.key)) {
                return entry.level;
            }
        }
        return 0;
    }

    @Override
    public Iterator<Entry> iterator() {
        return new EntryIterator(entries);
    }

    public static final class Entry {
        private final String key;
        private final PitEnchantment enchantment;
        private final int level;

        Entry(String key, PitEnchantment enchantment, int level) {
            this.key = key;
            this.enchantment = enchantment;
            this.level = level;
        }

        public String getKey() {
            return key;
        }

        public PitEnchantment getEnchantment() {
            return enchantment;
        }

        public boolean isKnown() {
            return enchantment != null;
        }

        public int getLevel() {
            return level;
        }
    }
    private static final class EntryIterator implements Iterator<Entry> {
        private final Entry[] entries;
        private int index;

        private EntryIterator(Entry[] entries) {
            this.entries = entries;
        }

        @Override
        public boolean hasNext() {
            return index < entries.length;
        }

        @Override
        public Entry next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return entries[index++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Pit enchantments are immutable");
        }
    }
}
