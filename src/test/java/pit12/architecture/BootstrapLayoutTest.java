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
package pit12.architecture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import pit12.feature.webui.WebUiConfig;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.languages.Languages;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.lifecycle.ClientLifecycle;

public final class BootstrapLayoutTest {
    @Test
    public void sectionsAndRegistrationOrderMatchCategoriesAndDependencies() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/pit12/bootstrap/ClientBootstrap.java")),
                StandardCharsets.UTF_8);
        String code = maskCommentsAndStrings(source);
        Matcher constructor = Pattern.compile("public ClientBootstrap\\(\\)\\s*\\{").matcher(code);
        assertTrue("Missing Bootstrap constructor", constructor.find());
        int constructorStart = constructor.end();
        String constructorBody =
                code.substring(constructorStart, bodyEnd(code, constructorStart - 1));
        List<String> groups = new ArrayList<>();
        Matcher headings = Pattern.compile(
                "(?m)^[ \\t]*// (Shared runtime|Feature providers|Features|Platform integrations)[ \\t]*$")
                .matcher(source.substring(constructorStart,
                        constructorStart + constructorBody.length()));
        while (headings.find()) {
            groups.add(headings.group(1));
        }
        assertEquals("Constructor sections", Arrays.asList("Shared runtime", "Feature providers",
                "Features", "Platform integrations"), groups);
        Map<String, String> imports = new HashMap<>();
        Matcher imported = Pattern.compile("import ([\\w.]+);").matcher(code);
        while (imported.find()) {
            String name = imported.group(1);
            imports.put(name.substring(name.lastIndexOf('.') + 1), name);
        }
        Map<String, Registration> registrations = new LinkedHashMap<>();
        Set<String> configTypes = new HashSet<>();
        Matcher declarations = Pattern
                .compile("\\bprivate\\s+(\\w+)\\s+(register\\w+)\\([^{}]*\\)\\s*\\{").matcher(code);
        while (declarations.find()) {
            String name = declarations.group(2);
            String body = code.substring(declarations.end(), bodyEnd(code, declarations.end() - 1));
            ConfigCategory category = null;
            Matcher config = Pattern.compile("\\bnew\\s+(\\w+Config)\\s*\\(").matcher(body);
            if (config.find()) {
                String type = imports.get(config.group(1));
                assertNotNull("Missing config import in " + name, type);
                assertTrue("Config is constructed twice: " + type, configTypes.add(type));
                if (WebUiConfig.class.getName().equals(type)) {
                    category = new WebUiConfig(new Languages(ClientThread.current())).category();
                } else {
                    category = FeatureConfig.class
                            .cast(Class.forName(type).getDeclaredConstructor().newInstance())
                            .category();
                }
                assertFalse("Multiple configs in " + name, config.find());
            }
            Registration registration =
                    new Registration(!"void".equals(declarations.group(1)), category);
            assertEquals("Declaration section for " + name, registration.section(),
                    sectionAt(source, declarations.start()));
            assertEquals("Component registrations in " + name, 1,
                    calls(body, "components\\.add").size());
            assertEquals("Config registrations in " + name, category == null ? 0 : 1,
                    calls(body, "configs\\.register").size());
            assertTrue("Config freeze belongs in the constructor: " + name,
                    calls(body, "configs\\.freeze").isEmpty());
            assertTrue("Lifecycle calls belong in Bootstrap.start() and stop(): " + name,
                    calls(body, "start|stop|[\\w.]+\\.(?:start|stop)").isEmpty());
            assertFalse("Duplicate registration method: " + name, registrations.containsKey(name));
            registrations.put(name, registration);
        }
        assertFalse("Missing feature registration methods", registrations.isEmpty());
        Map<String, String> producers = new HashMap<>();
        Map<String, String> arguments = new LinkedHashMap<>();
        Set<String> inlineComponents = new HashSet<>();
        Set<String> platformComponents = new HashSet<>();
        Matcher objects = Pattern.compile("\\b(\\w+)\\s*=\\s*new\\s+(\\w+)\\(([^;]*)\\);")
                .matcher(constructorBody);
        while (objects.find()) {
            producers.put(objects.group(1), objects.group(1));
            arguments.put(objects.group(1), objects.group(3));
            String type = imports.get(objects.group(2));
            assertNotNull("Missing component import: " + objects.group(2), type);
            if (ClientLifecycle.class
                    .isAssignableFrom(Class.forName(type, false, getClass().getClassLoader()))) {
                inlineComponents.add(objects.group(1));
            }
            if (type.startsWith("pit12.platform.")) {
                platformComponents.add(objects.group(1));
            }
            assertEquals("Component creation section for " + objects.group(1),
                    Arrays.asList(
                            platformComponents.contains(objects.group(1)) ? "Platform integrations"
                                    : "Shared runtime",
                            null),
                    sectionAt(source, constructorStart + objects.start()));
        }
        Matcher results =
                Pattern.compile("\\b(\\w+)\\s*=\\s*(register\\w+)\\(").matcher(constructorBody);
        while (results.find()) {
            producers.put(results.group(1), results.group(2));
        }
        List<String> callOrder = new ArrayList<>();
        List<String> componentOrder = new ArrayList<>();
        for (Call call : calls(constructorBody, "register\\w+|components\\.add")) {
            if ("components.add".equals(call.name)) {
                String component = producers.get(call.arguments.trim());
                assertNotNull("Unknown component: " + call.arguments, component);
                assertEquals("Component registration section",
                        Arrays.asList(
                                platformComponents.contains(component) ? "Platform integrations"
                                        : "Shared runtime",
                                null),
                        sectionAt(source, constructorStart + call.offset));
                componentOrder.add(component);
            } else {
                Registration registration = registrations.get(call.name);
                assertNotNull("Unknown feature registration: " + call.name, registration);
                assertEquals("Call section for " + call.name, registration.section(),
                        sectionAt(source, constructorStart + call.offset));
                callOrder.add(call.name);
                componentOrder.add(call.name);
                arguments.put(call.name, call.arguments);
            }
        }
        assertEquals("Each component must be registered once", componentOrder.size(),
                new HashSet<>(componentOrder).size());
        Set<String> expectedComponents = new HashSet<>(inlineComponents);
        expectedComponents.addAll(registrations.keySet());
        assertEquals("Register every component and feature", expectedComponents,
                new HashSet<>(componentOrder));
        assertEquals("Declarations and calls must have the same order",
                new ArrayList<>(registrations.keySet()), callOrder);
        assertEquals("Freeze the catalog once", 1,
                calls(constructorBody, "configs\\.freeze").size());
        assertTrue("Freeze the catalog after all registrations",
                constructorBody.trim().endsWith("configs.freeze();"));
        assertTrue("Do not start or stop components during assembly",
                calls(constructorBody, "start|stop|[\\w.]+\\.(?:start|stop)").isEmpty());
        Map<String, Set<String>> dependencies = new HashMap<>();
        Set<String> providers = new HashSet<>();
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            Set<String> required = new HashSet<>();
            Matcher identifiers = Pattern.compile("\\b\\w+\\b").matcher(entry.getValue());
            while (identifiers.find()) {
                String producer = producers.get(identifiers.group());
                if (producer != null) {
                    required.add(producer);
                    if (registrations.containsKey(entry.getKey())
                            && registrations.containsKey(producer)) {
                        providers.add(producer);
                    }
                }
            }
            dependencies.put(entry.getKey(), required);
        }
        for (Map.Entry<String, Registration> entry : registrations.entrySet()) {
            assertEquals("Return an API only for feature providers: " + entry.getKey(),
                    providers.contains(entry.getKey()), entry.getValue().provider);
        }
        Map<String, Integer> componentPositions = new HashMap<>();
        for (int index = 0; index < componentOrder.size(); index++) {
            componentPositions.put(componentOrder.get(index), index);
        }
        for (String component : componentOrder) {
            for (String dependency : dependencies.get(component)) {
                Integer dependencyPosition = componentPositions.get(dependency);
                if (dependencyPosition != null) {
                    assertTrue(dependency + " must start before " + component,
                            dependencyPosition < componentPositions.get(component));
                }
            }
        }
        Comparator<String> order =
                Comparator.comparingInt((String name) -> registrations.get(name).provider ? 0 : 1)
                        .thenComparingInt(
                                name -> registrations.get(name).category == null ? Integer.MIN_VALUE
                                        : registrations.get(name).category.displayOrder())
                        .thenComparing(name -> registrations.get(name).category == null ? ""
                                : registrations.get(name).category.id())
                        .thenComparing(Comparator.naturalOrder());
        Set<String> remaining = new HashSet<>(registrations.keySet());
        Set<String> completed = new HashSet<>();
        for (String name : callOrder) {
            String expected = remaining.stream()
                    .filter(candidate -> dependencies.get(candidate).stream()
                            .filter(registrations::containsKey).allMatch(completed::contains))
                    .min(order).orElseThrow(() -> new AssertionError(
                            "Feature registration dependencies contain a cycle"));
            assertEquals("Next registration by group, category, and name", expected, name);
            remaining.remove(name);
            completed.add(name);
        }
    }

    private static List<Call> calls(String code, String names) {
        List<Call> calls = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\b(" + names + ")\\s*\\(([^;]*)\\);").matcher(code);
        while (matcher.find()) {
            calls.add(new Call(matcher.group(1), matcher.group(2), matcher.start()));
        }
        return calls;
    }

    private static List<String> sectionAt(String source, int offset) {
        String group = null;
        String category = null;
        Matcher matcher = Pattern.compile(
                "(?m)^[ \\t]*// (Shared runtime|Feature providers|Features|Platform integrations|Category: [^\\r\\n]+)$")
                .matcher(source.substring(0, offset));
        while (matcher.find()) {
            String heading = matcher.group(1).trim();
            if (heading.startsWith("Category: ")) {
                category = heading.substring("Category: ".length());
            } else {
                group = heading;
                category = null;
            }
        }
        return Arrays.asList(group, category);
    }

    private static String maskCommentsAndStrings(String source) {
        // Spaces keep source offsets valid for the section lookups.
        char[] code = source.toCharArray();
        Matcher matcher = Pattern.compile(
                "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/")
                .matcher(source);
        while (matcher.find()) {
            for (int index = matcher.start(); index < matcher.end(); index++) {
                if (code[index] != '\r' && code[index] != '\n') {
                    code[index] = ' ';
                }
            }
        }
        return new String(code);
    }

    private static int bodyEnd(String code, int start) {
        int depth = 0;
        for (int index = start; index < code.length(); index++) {
            if (code.charAt(index) == '{') {
                depth++;
            } else if (code.charAt(index) == '}' && --depth == 0) {
                return index;
            }
        }
        throw new AssertionError("Unclosed method body");
    }

    private static final class Registration {
        private final boolean provider;
        private final ConfigCategory category;

        private Registration(boolean provider, ConfigCategory category) {
            this.provider = provider;
            this.category = category;
        }

        private List<String> section() {
            return Arrays.asList(provider ? "Feature providers" : "Features",
                    category == null ? "No config" : category.displayName());
        }
    }
    private static final class Call {
        private final String name;
        private final String arguments;
        private final int offset;

        private Call(String name, String arguments, int offset) {
            this.name = name;
            this.arguments = arguments;
            this.offset = offset;
        }
    }
}
