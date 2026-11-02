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
package pit12.runtime.item;

import java.util.Arrays;
import java.util.Iterator;
import net.minecraft.util.EnumChatFormatting;

public final class PitEnchantments implements Iterable<PitEnchantments.Entry> {
    private static final PitEnchantments EMPTY = new PitEnchantments(new Entry[0], false);
    private final Entry[] entries;
    private final boolean gemmed;
    // An empty string caches a result with no known enchantments.
    private volatile String[] displayNames;

    private PitEnchantments(Entry[] entries, boolean gemmed) {
        this.entries = entries;
        this.gemmed = gemmed;
    }

    static PitEnchantments create(Entry[] entries, int size, boolean gemmed) {
        if (size == 0 && !gemmed) {
            return EMPTY;
        }
        // The reader is done with this array. It's ours now.
        return new PitEnchantments(size == entries.length ? entries : Arrays.copyOf(entries, size),
                gemmed);
    }

    public static PitEnchantments empty() {
        return EMPTY;
    }

    /** Returns null when this result contains no known enchantments. */
    public String formatDisplayNames(boolean showGem) {
        return formatDisplayNames(PitEnchantmentFormat.PLAIN_LEVELS, showGem);
    }

    /** Returns null when this result contains no known enchantments. */
    public String formatBoldDisplayNames(boolean showGem) {
        return formatDisplayNames(PitEnchantmentFormat.BOLD_LEVELS, showGem);
    }

    /** Returns null when this result contains no known enchantments. */
    public String formatBoldDisplayNamesWithoutLevelThree(boolean showGem) {
        return formatDisplayNames(PitEnchantmentFormat.HIDE_LEVEL_THREE, showGem);
    }

    /** Returns null when this result contains no known enchantments. */
    public String formatBoldDisplayNamesWithoutLevels(boolean showGem) {
        return formatDisplayNames(PitEnchantmentFormat.NAMES_ONLY, showGem);
    }

    public boolean gemmed() {
        return gemmed;
    }

    public boolean contains(PitEnchantment enchantment) {
        for (Entry entry : entries) {
            if (entry.enchantment == enchantment) {
                return true;
            }
        }
        return false;
    }

    public boolean sameContents(PitEnchantments other) {
        if (this == other) {
            return true;
        }
        if (gemmed != other.gemmed || entries.length != other.entries.length) {
            return false;
        }
        for (int index = 0; index < entries.length; index++) {
            Entry entry = entries[index];
            Entry otherEntry = other.entries[index];
            if (entry.level != otherEntry.level || !entry.key.equals(otherEntry.key)) {
                return false;
            }
        }
        return true;
    }

    private String formatDisplayNames(int format, boolean showGem) {
        int index = format * 2 + (showGem && gemmed ? 1 : 0);
        String[] cached = displayNames;
        String text = cached == null ? null : cached[index];
        if (text == null) {
            text = cacheDisplayNames(index);
        }
        return text.isEmpty() ? null : text;
    }

    // Equipment snapshots can also be formatted by workers.
    private synchronized String cacheDisplayNames(int index) {
        if (displayNames == null) {
            displayNames = new String[8];
        }
        String cached = displayNames[index];
        if (cached != null) {
            return cached;
        }
        int format = index / 2;
        boolean bold = format != PitEnchantmentFormat.PLAIN_LEVELS;
        boolean showLevels = format != PitEnchantmentFormat.NAMES_ONLY;
        boolean hideLevelThree = format == PitEnchantmentFormat.HIDE_LEVEL_THREE;
        StringBuilder text = null;
        for (Entry entry : entries) {
            PitEnchantment enchantment = entry.enchantment;
            if (enchantment == null) {
                continue;
            }
            if (text == null) {
                text = new StringBuilder(48);
                text.append(EnumChatFormatting.RESET);
            } else {
                text.append(EnumChatFormatting.GRAY).append(" / ").append(EnumChatFormatting.RESET);
            }
            appendDisplayName(text, enchantment.getDisplayName(), bold);
            if (showLevels && (!hideLevelThree || entry.level != 3)) {
                text.append(' ').append(EnumChatFormatting.WHITE);
                if (bold) {
                    text.append(EnumChatFormatting.BOLD);
                }
                text.append(entry.level);
            }
        }
        String result = text == null ? "" : (index & 1) != 0 ? text + " §a♦" : text.toString();
        displayNames[index] = result;
        return result;
    }

    private static void appendDisplayName(StringBuilder text, String displayName, boolean bold) {
        if (!bold) {
            text.append(displayName);
            return;
        }
        // Color codes clear bold, so add it after the color.
        if (displayName.length() >= 2 && displayName.charAt(0) == '§') {
            text.append(displayName, 0, 2).append(EnumChatFormatting.BOLD).append(displayName, 2,
                    displayName.length());
        } else {
            text.append(EnumChatFormatting.BOLD).append(displayName);
        }
    }

    @Override
    // noinspection UnannotatedMethodOverridesNotNullMethod
    public Iterator<Entry> iterator() {
        return Arrays.asList(entries).iterator();
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

        public int getLevel() {
            return level;
        }
    }
}
