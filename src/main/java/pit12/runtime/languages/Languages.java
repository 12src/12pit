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

import static edu.umd.cs.findbugs.annotations.SuppressMatchType.EXACT;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pit12.shared.concurrent.ClientThread;

public final class Languages {
    private static final Pattern PARAMETER = Pattern.compile("\\{([0-9]+)}");
    private static final Pattern LOCALE = Pattern.compile("[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*");
    private static final Map<String, String> MARKED_TEXTS = new HashMap<>();
    private static final Map<String, String> SOURCE_LOCATIONS = new IdentityHashMap<>();
    private final ClientThread client;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, PoCatalog> catalogs = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private String locale = "en-us";
    private PoCatalog translations;

    public Languages(ClientThread client) {
        this.client = client;
        try (BufferedReader reader = resource("languages.json")) {
            for (JsonElement value : new JsonParser().parse(reader).getAsJsonArray()) {
                JsonObject object = value.getAsJsonObject();
                String code = object.get("locale").getAsString();
                String name = object.get("name").getAsString();
                String displayName = object.get("displayName").getAsString();
                if (!LOCALE.matcher(code).matches() || name.trim().isEmpty()
                        || displayName.trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid language entry");
                }
                String normalized = code.toLowerCase(Locale.ROOT);
                if (entries.put(normalized,
                        new Entry(code, normalized, name, displayName)) != null) {
                    throw new IllegalArgumentException("Duplicate language " + code);
                }
            }
            if (!entries.containsKey("en-us")) {
                throw new IllegalArgumentException("English is missing");
            }
            List<Entry> sorted = new ArrayList<>(entries.values());
            sorted.sort(Comparator.comparing((Entry entry) -> !entry.locale.equals("en-us"))
                    .thenComparing(entry -> entry.displayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> entry.locale));
            entries.clear();
            for (Entry entry : sorted) {
                entries.put(entry.locale, entry);
            }
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Could not read languages.json", failure);
        }
    }

    public Map<String, String> names() {
        Map<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            names.put(entry.getKey(), entry.getValue().name);
        }
        return names;
    }

    public String locale() {
        return locale;
    }

    public Map<String, String> displayNameSource(String locale) {
        Entry entry = entry(locale);
        Map<String, String> source = new HashMap<>();
        source.put("text", entry.displayName);
        source.put("location",
                "src/main/resources/assets/pit12/languages/languages.json:" + entry.file);
        return source;
    }

    // Give each usage its own string so shared literals can have different locations.
    @SuppressWarnings("PMD.StringInstantiation")
    @SuppressFBWarnings(value = "DM_STRING_CTOR", matchType = EXACT)
    public static String source(String text) {
        String location = location();
        String key = location + '\0' + text;
        synchronized (SOURCE_LOCATIONS) {
            String marked = MARKED_TEXTS.get(key);
            if (marked == null) {
                // The fresh identity is the point. See the comment above.
                // noinspection RedundantStringConstructorCall
                marked = new String(text);
                MARKED_TEXTS.put(key, marked);
                SOURCE_LOCATIONS.put(marked, location);
            }
            return marked;
        }
    }

    /** Returns plain text or an object with text and location for the Web UI. */
    public static Object sourceText(String text) {
        String location;
        synchronized (SOURCE_LOCATIONS) {
            location = SOURCE_LOCATIONS.get(text);
        }
        if (location == null) {
            return text;
        }
        Map<String, String> source = new HashMap<>();
        source.put("text", text);
        source.put("location", location);
        return source;
    }

    public void select(String locale) {
        client.check();
        Entry entry = entry(locale);
        if (this.locale.equals(entry.locale)) {
            return;
        }
        PoCatalog values = load(entry);
        translations = entry.locale.equals("en-us") ? null : values;
        this.locale = entry.locale;
        for (Runnable listener : listeners.toArray(new Runnable[0])) {
            listener.run();
        }
    }

    public String translate(String source) {
        return translate(translations, source);
    }

    public String translate(String source, boolean enabled) {
        return enabled ? translate(source) : source;
    }

    public String name(String locale) {
        return entry(locale).name;
    }

    /** A text translated into the given language. */
    public String text(String locale, String source) {
        return translate(load(entry(locale)), source);
    }

    private String translate(PoCatalog catalog, String source) {
        if (catalog == null) {
            return source;
        }
        String translated = catalog.shared.get(source);
        if (translated != null) {
            return translated;
        }
        Map<String, String> variants = catalog.contextual.get(source);
        if (variants == null) {
            return source;
        }
        String location;
        synchronized (SOURCE_LOCATIONS) {
            location = SOURCE_LOCATIONS.get(source);
        }
        translated = variants.get(location == null ? location() : location);
        return translated == null ? source : translated;
    }

    public String format(String source, Object... parameters) {
        String translated = translate(source);
        Matcher matcher = PARAMETER.matcher(translated);
        StringBuilder result = new StringBuilder(translated.length());
        int start = 0;
        while (matcher.find()) {
            result.append(translated, start, matcher.start());
            result.append(parameters[Integer.parseInt(matcher.group(1))]);
            start = matcher.end();
        }
        return result.append(translated, start, translated.length()).toString();
    }

    public Map<String, Object> texts(String requested) {
        return load(entry(requested)).texts();
    }

    /** Runs the listener immediately and after each language change. */
    public void addListener(Runnable listener) {
        client.check();
        listeners.add(listener);
        listener.run();
    }

    public void removeListener(Runnable listener) {
        client.check();
        listeners.remove(listener);
    }

    private Entry entry(String locale) {
        Entry entry = entries.get(locale.toLowerCase(Locale.ROOT));
        if (entry == null) {
            throw new IllegalArgumentException("Unknown language: " + locale);
        }
        return entry;
    }

    private PoCatalog load(Entry entry) {
        if (entry.locale.equals("en-us")) {
            return new PoCatalog();
        }
        PoCatalog values = catalogs.get(entry.locale);
        if (values != null) {
            return values;
        }
        String file = entry.file + ".po";
        try (BufferedReader reader = resource(file)) {
            values = PoCatalog.read(reader, file);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Could not read language file " + file, failure);
        }
        catalogs.put(entry.locale, values);
        return values;
    }

    private static String location() {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        int index = 1;
        while (trace[index].getClassName().equals(Languages.class.getName())) {
            index++;
        }
        StackTraceElement caller = trace[index];
        String name = caller.getClassName();
        return "src/main/java/" + name.substring(0, name.lastIndexOf('.') + 1).replace('.', '/')
                + caller.getFileName() + ":" + caller.getLineNumber();
    }

    private static BufferedReader resource(String file) {
        InputStream stream = Languages.class.getResourceAsStream("/assets/pit12/languages/" + file);
        if (stream == null) {
            throw new IllegalStateException("Missing language resource " + file);
        }
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static final class Entry {
        private final String file;
        private final String locale;
        private final String name;
        private final String displayName;

        private Entry(String file, String locale, String name, String displayName) {
            this.file = file;
            this.locale = locale;
            this.name = name;
            this.displayName = displayName;
        }
    }
}
