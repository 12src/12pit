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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
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
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LOCATION = Pattern.compile("src/main/java/.+\\.java:[1-9][0-9]*"
            + "|web-ui/src/.+\\.(?:vue|ts):[1-9][0-9]*(?::[1-9][0-9]*)?"
            + "|src/main/resources/assets/pit12/languages/languages\\.json:"
            + "[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*");
    private static final Pattern PO_FIELD = Pattern.compile("(msgctxt|msgid|msgstr)\\s+(\".*\")");
    private static final Map<String, String> MARKED_TEXTS = new HashMap<>();
    private static final Map<String, String> SOURCE_LOCATIONS = new IdentityHashMap<>();
    private final ClientThread client;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Catalog> catalogs = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private String locale = "en-us";
    private Catalog translations;

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
        Catalog values = load(entry);
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

    private String translate(Catalog catalog, String source) {
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
        Catalog catalog = load(entry(requested));
        Map<String, Object> texts = new HashMap<>(catalog.shared);
        for (Map.Entry<String, Map<String, String>> text : catalog.contextual.entrySet()) {
            texts.put(text.getKey(), new HashMap<>(text.getValue()));
        }
        return texts;
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

    private Catalog load(Entry entry) {
        if (entry.locale.equals("en-us")) {
            return new Catalog();
        }
        Catalog values = catalogs.get(entry.locale);
        if (values != null) {
            return values;
        }
        values = new Catalog();
        String file = entry.file + ".po";
        try (BufferedReader reader = resource(file)) {
            PoMessage message = new PoMessage();
            StringBuilder current = null;
            int number = 0;
            while (true) {
                String line = reader.readLine();
                number++;
                if (line != null) {
                    if (number == 1 && line.startsWith("\uFEFF")) {
                        line = line.substring(1);
                    }
                    line = line.trim();
                }
                if (line == null || line.isEmpty()
                        || (message.fields.containsKey("msgstr") && (line.startsWith("#")
                                || line.startsWith("msgctxt") || line.startsWith("msgid")))) {
                    message.addTo(values);
                    message = new PoMessage();
                    current = null;
                }
                if (line == null) {
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#")) {
                    current = null;
                    if (line.startsWith("#:")) {
                        String references = line.substring(2).trim();
                        if (!references.isEmpty()) {
                            Collections.addAll(message.locations, WHITESPACE.split(references));
                        }
                    } else if (line.startsWith("#,")) {
                        for (String flag : line.substring(2).split(",")) {
                            if (flag.trim().equals("fuzzy")) {
                                message.fuzzy = true;
                                break;
                            }
                        }
                    }
                    continue;
                }
                String quoted;
                if (line.startsWith("\"")) {
                    if (current == null) {
                        throw new IllegalArgumentException(
                                file + ":" + number + ": unexpected PO continuation");
                    }
                    quoted = line;
                } else {
                    Matcher field = PO_FIELD.matcher(line);
                    if (!field.matches()) {
                        throw new IllegalArgumentException(
                                file + ":" + number + ": unsupported PO line");
                    }
                    current = new StringBuilder();
                    if (message.fields.put(field.group(1), current) != null) {
                        throw new IllegalArgumentException(
                                file + ":" + number + ": duplicate PO field");
                    }
                    quoted = field.group(2);
                }
                current.append(decode(quoted));
            }
            // Same translation everywhere, so we can skip the caller lookup.
            Iterator<Map.Entry<String, Map<String, String>>> iterator =
                    values.contextual.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Map<String, String>> variants = iterator.next();
                String text = variants.getValue().values().iterator().next();
                boolean shared = true;
                for (String translation : variants.getValue().values()) {
                    if (!text.equals(translation)) {
                        shared = false;
                        break;
                    }
                }
                if (shared) {
                    values.shared.put(variants.getKey(), text);
                    iterator.remove();
                }
            }
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

    private static String decode(String quoted) {
        if (quoted.length() < 2 || quoted.charAt(0) != '"'
                || quoted.charAt(quoted.length() - 1) != '"') {
            throw new IllegalArgumentException("Invalid PO string");
        }
        StringBuilder result = new StringBuilder(quoted.length() - 2);
        for (int index = 1; index < quoted.length() - 1; index++) {
            char character = quoted.charAt(index);
            if (character == '\\') {
                if (++index == quoted.length() - 1) {
                    throw new IllegalArgumentException("Incomplete PO escape");
                }
                character = quoted.charAt(index);
                switch (character) {
                    case 'a':
                        character = '\u0007';
                        break;
                    case 'b':
                        character = '\b';
                        break;
                    case 'f':
                        character = '\f';
                        break;
                    case 'n':
                        character = '\n';
                        break;
                    case 'r':
                        character = '\r';
                        break;
                    case 't':
                        character = '\t';
                        break;
                    case 'v':
                        character = '\u000B';
                        break;
                    case '\\':
                    case '"':
                        break;
                    default:
                        if (character < '0' || character > '7') {
                            throw new IllegalArgumentException("Unknown PO escape");
                        }
                        int value = character - '0';
                        for (int count = 1; count < 3 && index + 1 < quoted.length() - 1; count++) {
                            char digit = quoted.charAt(index + 1);
                            if (digit < '0' || digit > '7') {
                                break;
                            }
                            value = value * 8 + digit - '0';
                            index++;
                        }
                        character = (char) value;
                }
            } else if (character == '"' || character < ' ') {
                throw new IllegalArgumentException("Invalid PO string character");
            }
            result.append(character);
        }
        return result.toString();
    }

    private static final class PoMessage {
        private final Map<String, StringBuilder> fields = new HashMap<>();
        private final List<String> locations = new ArrayList<>();
        private boolean fuzzy;

        private void addTo(Catalog catalog) {
            if (fields.isEmpty()) {
                return;
            }
            StringBuilder original = fields.get("msgid");
            StringBuilder translated = fields.get("msgstr");
            if (original == null || translated == null) {
                throw new IllegalArgumentException("Incomplete PO entry");
            }
            String source = original.toString();
            if (source.isEmpty() && !fields.containsKey("msgctxt")) {
                return; // The empty msgid holds the PO header.
            }
            StringBuilder context = fields.get("msgctxt");
            if (context == null || context.length() == 0 || source.isEmpty()
                    || locations.isEmpty()) {
                throw new IllegalArgumentException(
                        "PO entries need a context, source, and usage location");
            }
            String target = fuzzy || translated.length() == 0 ? source : translated.toString();
            Map<String, String> variants =
                    catalog.contextual.computeIfAbsent(source, key -> new HashMap<>());
            for (String location : locations) {
                if (!LOCATION.matcher(location).matches()
                        || variants.put(location, target) != null) {
                    throw new IllegalArgumentException("Invalid or duplicate PO usage " + location);
                }
            }
        }
    }
    private static final class Catalog {
        private final Map<String, String> shared = new HashMap<>();
        private final Map<String, Map<String, String>> contextual = new HashMap<>();
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
