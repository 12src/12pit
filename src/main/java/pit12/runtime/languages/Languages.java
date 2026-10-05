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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pit12.runtime.config.ChoiceSetting.Choice;
import pit12.shared.concurrent.ClientThread;

public final class Languages {
    private static final Pattern PARAMETER = Pattern.compile("\\{([0-9]+)\\}");
    private final ClientThread client;
    private final Map<Integer, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> catalogs = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private String locale = "en-us";
    private Map<String, String> translations;

    public Languages(ClientThread client) {
        this.client = client;
        try (BufferedReader reader = resource("languages.json")) {
            for (JsonElement value : new JsonParser().parse(reader).getAsJsonArray()) {
                JsonObject object = value.getAsJsonObject();
                JsonElement id = object.get("value");
                int number = id.getAsInt();
                String code = object.get("locale").getAsString();
                String name = object.get("name").getAsString();
                if (!id.getAsJsonPrimitive().isNumber() || number < 0 || id.getAsDouble() != number
                        || !code.matches("[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*")
                        || name.trim().isEmpty()
                        || (number == 0) != code.equalsIgnoreCase("en-us")) {
                    throw new IllegalArgumentException("Invalid language entry");
                }
                String normalized = code.toLowerCase(Locale.ROOT);
                for (Entry entry : entries.values()) {
                    if (entry.locale.equals(normalized)) {
                        throw new IllegalArgumentException("Duplicate language " + code);
                    }
                }
                if (entries.put(number, new Entry(code, normalized, name)) != null) {
                    throw new IllegalArgumentException("Duplicate language value " + number);
                }
            }
            if (!entries.containsKey(0)) {
                throw new IllegalArgumentException("English is missing");
            }
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Could not read languages.json", failure);
        }
    }

    public Choice[] choices() {
        Choice[] choices = new Choice[entries.size()];
        int index = 0;
        for (Map.Entry<Integer, Entry> entry : entries.entrySet()) {
            choices[index++] = new Choice(entry.getKey(), entry.getValue().name);
        }
        return choices;
    }

    public String locale() {
        return locale;
    }

    // The script reads these calls; keeping English lets display caches switch languages.
    public static String source(String text) {
        return text;
    }

    public void select(int value) {
        client.check();
        Entry entry = entries.get(value);
        if (locale.equals(entry.locale)) {
            return;
        }
        Map<String, String> values = load(entry);
        translations = value == 0 ? null : values;
        locale = entry.locale;
        for (Runnable listener : listeners.toArray(new Runnable[listeners.size()])) {
            listener.run();
        }
    }

    public String translate(String source) {
        if (translations == null) {
            return source;
        }
        String translated = translations.get(source);
        return translated == null ? source : translated;
    }

    public String translate(String source, boolean enabled) {
        return enabled ? translate(source) : source;
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

    public Map<String, String> texts(String requested) {
        for (Entry entry : entries.values()) {
            if (entry.locale.equals(requested)) {
                return new HashMap<>(load(entry));
            }
        }
        throw new IllegalArgumentException("Unknown language: " + requested);
    }

    public void addListener(Runnable listener) {
        client.check();
        listeners.add(listener);
        listener.run();
    }

    public void removeListener(Runnable listener) {
        client.check();
        listeners.remove(listener);
    }

    private Map<String, String> load(Entry entry) {
        if (entry.locale.equals("en-us")) {
            return Collections.emptyMap();
        }
        Map<String, String> values = catalogs.get(entry.locale);
        if (values != null) {
            return values;
        }
        values = new HashMap<>();
        String file = entry.file + ".txt";
        try (BufferedReader reader = resource(file)) {
            String source;
            int number = 0;
            while ((source = reader.readLine()) != null) {
                number++;
                if (number == 1 && source.startsWith("\uFEFF")) {
                    source = source.substring(1);
                }
                if (source.isEmpty() || source.startsWith("//")) {
                    continue;
                }
                String target = reader.readLine();
                if (target == null) {
                    throw new IllegalArgumentException(
                            file + ":" + number + ": missing translation line");
                }
                source = decode(source);
                target = decode(target);
                number++;
                if (values.containsKey(source)) {
                    throw new IllegalArgumentException(
                            file + ":" + (number - 1) + ": duplicate source");
                }
                values.put(source, target.isEmpty() ? source : target);
            }
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Could not read language file " + file, failure);
        }
        catalogs.put(entry.locale, values);
        return values;
    }

    private static BufferedReader resource(String file) {
        InputStream stream = Languages.class.getResourceAsStream("/assets/pit12/languages/" + file);
        if (stream == null) {
            throw new IllegalStateException("Missing language resource " + file);
        }
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static String decode(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\\') {
                if (++index == text.length()) {
                    throw new IllegalArgumentException("Incomplete language escape");
                }
                switch (text.charAt(index)) {
                    case 'n':
                        character = '\n';
                        break;
                    case 'r':
                        character = '\r';
                        break;
                    case 't':
                        character = '\t';
                        break;
                    case '\\':
                        character = '\\';
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown language escape");
                }
            }
            result.append(character);
        }
        return result.toString();
    }

    private static final class Entry {
        private final String file;
        private final String locale;
        private final String name;

        private Entry(String file, String locale, String name) {
            this.file = file;
            this.locale = locale;
            this.name = name;
        }
    }
}
