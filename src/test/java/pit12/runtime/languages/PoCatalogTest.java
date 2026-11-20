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
package pit12.runtime.languages;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import org.junit.Test;

public final class PoCatalogTest {
    @Test
    public void keepsDifferentTranslationsByLocation() throws IOException {
        String po =
                "#: src/main/java/pit12/First.java:1\n" + "msgctxt \"menu\"\n" + "msgid \"Open\"\n"
                        + "msgstr \"foo\"\n\n" + "#: src/main/java/pit12/Second.java:2\n"
                        + "msgctxt \"editor\"\n" + "msgid \"Open\"\n" + "msgstr \"bar\"\n";
        Map<?, ?> variants = (Map<?, ?>) texts(po).get("Open");
        assertEquals("foo", variants.get("src/main/java/pit12/First.java:1"));
        assertEquals("bar", variants.get("src/main/java/pit12/Second.java:2"));
    }

    @Test
    public void sharesIdenticalTranslationsAcrossLocations() throws IOException {
        String po = "#: src/main/java/pit12/First.java:1 src/main/java/pit12/Second.java:2\n"
                + "msgctxt \"menu\"\n" + "msgid \"Open\"\n" + "msgstr \"foo\"\n";
        assertEquals("foo", texts(po).get("Open"));
    }

    @Test
    public void usesTheSourceForFuzzyTranslations() throws IOException {
        String po = "#: src/main/java/pit12/First.java:1\n" + "#, fuzzy\n" + "msgctxt \"menu\"\n"
                + "msgid \"Open\"\n" + "msgstr \"foo\"\n";
        assertEquals("Open", texts(po).get("Open"));
    }

    @Test
    public void usesTheSourceForEmptyTranslations() throws IOException {
        String po = "#: src/main/java/pit12/First.java:1\n" + "msgctxt \"menu\"\n"
                + "msgid \"Open\"\n" + "msgstr \"\"\n";
        assertEquals("Open", texts(po).get("Open"));
    }

    private static Map<String, Object> texts(String po) throws IOException {
        try (BufferedReader reader = new BufferedReader(new StringReader(po))) {
            return PoCatalog.read(reader, "sample.po").texts();
        }
    }
}
