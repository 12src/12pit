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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PoCatalog {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LOCATION = Pattern.compile("src/main/java/.+\\.java:[1-9][0-9]*"
            + "|web-ui/src/.+\\.(?:vue|ts):[1-9][0-9]*(?::[1-9][0-9]*)?"
            + "|src/main/resources/assets/pit12/languages/languages\\.json:"
            + "[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*");
    private static final Pattern PO_FIELD = Pattern.compile("(msgctxt|msgid|msgstr)\\s+(\".*\")");
    final Map<String, String> shared = new HashMap<>();
    final Map<String, Map<String, String>> contextual = new HashMap<>();

    static PoCatalog read(BufferedReader reader, String file) throws IOException {
        PoCatalog values = new PoCatalog();
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
        return values;
    }

    Map<String, Object> texts() {
        Map<String, Object> texts = new HashMap<>(shared);
        for (Map.Entry<String, Map<String, String>> text : contextual.entrySet()) {
            texts.put(text.getKey(), new HashMap<>(text.getValue()));
        }
        return texts;
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

        private void addTo(PoCatalog catalog) {
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
}
