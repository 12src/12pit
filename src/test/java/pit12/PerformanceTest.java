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
package pit12;

import static org.junit.Assert.assertTrue;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaConstructorReference;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.junit.Test;
import pit12.shared.performance.HotPath;

public final class PerformanceTest {
    @Test
    public void hotPathsAvoidRepeatedAllocations() {
        JavaClasses production = new ClassFileImporter().withImportOption(new DoNotIncludeTests())
                .importPackages("pit12");
        boolean found = false;
        for (JavaClass type : production) {
            for (JavaMethod method : type.getMethods()) {
                if (method.isAnnotatedWith(HotPath.class))
                    found = true;
            }
        }
        assertTrue("Mark frequent entry points with @HotPath", found);
        Set<String> violations = violations(production);
        assertTrue(String.join("\n", violations), violations.isEmpty());
    }

    @Test
    public void traversalAllowsArraysMapsSetsLinkedListsAndUnknownListTypes() {
        JavaClasses examples = new ClassFileImporter().importClasses(SafeExamples.class);
        Set<String> violations = violations(examples);
        assertTrue(String.join("\n", violations), violations.isEmpty());
    }

    @Test
    public void checksFollowHelpersAndRecursiveCalls() {
        JavaClasses examples = new ClassFileImporter().importClasses(HelperExamples.class);
        String report = String.join("\n", violations(examples));
        assertTrue(report, report.contains("java.util.ArrayList.iterator("));
        assertTrue(report, report.contains("java.util.ArrayList.removeIf("));
    }

    @Test
    public void checksIncludeMethodReferencesAndConstructors() {
        JavaClasses examples = new ClassFileImporter().importClasses(ReferenceExamples.class,
                FormattedText.class, ReferencedText.class);
        String report = String.join("\n", violations(examples));
        assertTrue(report, report.contains("java.util.ArrayList.iterator("));
        assertTrue(report, report.contains("java.lang.String.format("));
        assertTrue(report, report.contains("java.lang.String.replaceAll("));
    }

    @Test
    public void textRulesFindStreamsFormattingAndRepeatedPatterns() {
        JavaClasses examples = new ClassFileImporter().importClasses(TextExamples.class);
        String report = String.join("\n", violations(examples));
        assertTrue(report, report.contains("java.lang.String.format("));
        assertTrue(report, report.contains("java.lang.String.replaceAll("));
        assertTrue(report, report.contains("java.util.regex.Pattern.compile("));
        assertTrue(report, report.contains(".stream("));
    }

    private static Set<String> violations(JavaClasses classes) {
        Set<String> owners = new HashSet<>();
        Deque<JavaCodeUnit> pending = new ArrayDeque<>();
        for (JavaClass type : classes) {
            owners.add(type.getName());
            for (JavaMethod method : type.getMethods()) {
                if (method.isAnnotatedWith(HotPath.class))
                    pending.addLast(method);
            }
        }
        Set<String> violations = new TreeSet<>();
        Set<JavaCodeUnit> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            JavaCodeUnit method = pending.removeFirst();
            if (!visited.add(method))
                continue;
            for (JavaAccess<?> access : method.getAccessesFromSelf()) {
                if (!(access instanceof JavaMethodCall || access instanceof JavaMethodReference
                        || access instanceof JavaConstructorCall
                        || access instanceof JavaConstructorReference))
                    continue;
                AccessTarget target = access.getTarget();
                String reason = allocationReason(target);
                if (reason != null)
                    violations.add(access.getDescription() + ". " + reason);
                target.resolveMember().ifPresent(member -> {
                    if (member instanceof JavaCodeUnit
                            && owners.contains(member.getOwner().getName()))
                        pending.addLast((JavaCodeUnit) member);
                });
            }
        }
        return violations;
    }

    private static String allocationReason(AccessTarget target) {
        JavaClass owner = target.getOwner();
        String name = target.getName();
        if (owner.isAssignableTo(List.class) && owner.isAssignableTo(RandomAccess.class)
                && name.equals("iterator"))
            return "Use indexed traversal for array-backed lists in hot paths.";
        if (owner.isAssignableTo(ArrayList.class) && name.equals("removeIf"))
            return "Use in-place removal. Java 8 ArrayList.removeIf creates a BitSet.";
        if (owner.getPackageName().equals("java.util.stream")
                || owner.isAssignableTo(Collection.class)
                        && (name.equals("stream") || name.equals("parallelStream"))
                || owner.getName().equals("java.util.Arrays") && name.equals("stream"))
            return "Use direct traversal in hot paths.";
        if (owner.getName().equals("java.lang.String") && name.equals("format"))
            return "Build display text when its values change.";
        if (owner.getName().equals("java.lang.String")
                && (name.equals("matches") || name.equals("replaceAll")
                        || name.equals("replaceFirst"))
                || owner.getName().equals("java.util.regex.Pattern")
                        && (name.equals("compile") || name.equals("matches")))
            return "Compile the pattern once and reuse it in hot paths.";
        return null;
    }

    private static final class SafeExamples {
        private static final Pattern PATTERN = Pattern.compile("[a-z]+");

        @HotPath
        static int sum(String[] array, Map<String, String> map, Set<String> set,
                LinkedList<String> linked, List<String> unknown) {
            int length = 0;
            for (String value : array)
                length += value.length();
            for (String value : map.values())
                length += value.length();
            for (String value : set)
                length += value.length();
            for (String value : linked)
                length += value.length();
            for (String value : unknown)
                length += value.length();
            return length;
        }

        @HotPath
        static boolean cachedPattern(String value) {
            return PATTERN.matcher(value).matches();
        }

        static String occasionalFormatting(String value) {
            return String.format("%s", value);
        }
    }
    private static final class HelperExamples {
        @HotPath
        static int averageLength(ArrayList<String> values) {
            return sum(values, 1) / values.size();
        }

        private static int sum(ArrayList<String> values, int depth) {
            if (depth > 0)
                return sum(values, depth - 1);
            int length = 0;
            for (String value : values)
                length += value.length();
            values.removeIf(String::isEmpty);
            return length;
        }
    }
    private static final class ReferenceExamples {
        @HotPath
        static Iterator<String> iterator(ArrayList<String> values) {
            Supplier<Iterator<String>> iterator = values::iterator;
            return iterator.get();
        }

        @HotPath
        static String text(String value) {
            return new FormattedText(value).text;
        }

        @HotPath
        static String referencedText(String value) {
            Function<String, ReferencedText> factory = ReferencedText::new;
            return factory.apply(value).text;
        }
    }
    private static final class FormattedText {
        private final String text;

        FormattedText(String value) {
            text = String.format("%s", value);
        }
    }
    private static final class ReferencedText {
        private final String text;

        ReferencedText(String value) {
            text = value.replaceAll("[a-z]", "");
        }
    }
    private static final class TextExamples {
        @HotPath
        static String repeatedSetup(List<String> values) {
            return String.format("%s", values.stream().count())
                    + values.get(0).replaceAll("[a-z]", "")
                    + Pattern.compile("[a-z]+").matcher(values.get(0)).matches();
        }
    }
}
