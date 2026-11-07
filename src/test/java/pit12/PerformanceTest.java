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

import static edu.umd.cs.findbugs.annotations.SuppressMatchType.EXACT;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.text.Format;
import java.util.AbstractSequentialList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
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
        assertTrue("Mark per-frame entry points with @HotPath", found);
        Set<String> violations = violations(production);
        assertTrue(String.join("\n", violations), violations.isEmpty());
    }

    @Test
    public void traversalAllowsArraysMapsSetsAndSequentialLists() {
        JavaClasses examples = new ClassFileImporter().importClasses(SafeExamples.class);
        Set<String> violations = violations(examples);
        assertTrue(String.join("\n", violations), violations.isEmpty());
    }

    @Test
    public void staticAndPrivateMethodsDoNotReachUnrelatedSubclassMethods() {
        JavaClasses examples =
                new ClassFileImporter().importClasses(HiddenBase.class, HiddenChild.class);
        Set<String> violations = violations(examples);
        assertTrue(String.join("\n", violations), violations.isEmpty());
    }

    @Test
    public void checksFollowHelpersAndRecursiveCalls() {
        JavaClasses examples = new ClassFileImporter().importClasses(HelperExamples.class);
        Set<String> violations = violations(examples);
        assertViolation(violations, "HelperExamples.sum", "java.util.ArrayList.iterator(");
        assertViolation(violations, "HelperExamples.sum", "java.util.ArrayList.removeIf(");
    }

    @Test
    public void checksIncludeMethodReferencesAndConstructors() {
        JavaClasses examples = new ClassFileImporter().importClasses(ReferenceExamples.class,
                FormattedText.class, ReferencedText.class);
        Set<String> violations = violations(examples);
        assertViolation(violations, "ReferenceExamples.iterator", "java.util.ArrayList.iterator(");
        assertViolation(violations, "FormattedText.<init>", "java.lang.String.format(");
        assertViolation(violations, "ReferencedText.<init>", "java.lang.String.replaceAll(");
    }

    @Test
    public void textRulesFindStreamsFormattingAndRepeatedPatterns() {
        JavaClasses examples = new ClassFileImporter().importClasses(TextExamples.class);
        Set<String> violations = violations(examples);
        assertViolation(violations, "TextExamples.repeatedSetup", "java.lang.String.format(");
        assertViolation(violations, "TextExamples.repeatedSetup", "java.lang.String.replaceAll(");
        assertViolation(violations, "TextExamples.repeatedSetup",
                "java.util.regex.Pattern.compile(");
        assertViolation(violations, "TextExamples.repeatedSetup", "java.util.List.stream(");
    }

    @Test
    public void checksFollowInterfaceCallsOverridesAndStoredCallbackObjects() {
        JavaClasses examples = new ClassFileImporter().importClasses(DispatchExamples.class,
                TextSource.class, InterfaceText.class, BaseText.class, OverriddenText.class,
                FrameTask.class, FormattingTask.class);
        Set<String> violations = violations(examples);
        assertViolation(violations, "InterfaceText.text", "java.lang.String.format(");
        assertViolation(violations, "OverriddenText.text", "java.lang.String.replaceAll(");
        assertViolation(violations, "FormattingTask.run", "java.lang.String.format(");
    }

    @Test
    public void externalInterfacesDoNotReachUnrelatedImplementations() {
        JavaClasses examples = new ClassFileImporter().importClasses(ExternalCalls.class,
                ColdWorker.class, ColdIterable.class);
        Set<String> violations = violations(examples);
        assertTrue(String.join("\n", violations), violations.isEmpty());
    }

    @Test
    public void checksFollowConcreteCallbackMethods() {
        JavaClasses examples = new ClassFileImporter().importClasses(ConcreteCallback.class,
                FrameTask.class, FormattingTask.class);
        assertViolation(violations(examples), "FormattingTask.run", "java.lang.String.format(");
    }

    @Test
    public void hotContractsIncludeImplementationsWithoutDirectCallers() {
        JavaClasses examples = new ClassFileImporter().importClasses(HotTextSource.class,
                HotText.class, InheritedText.class, TextParent.class);
        Set<String> violations = violations(examples);
        assertViolation(violations, "HotText.text", "java.lang.String.format(");
        assertViolation(violations, "TextParent.text", "java.lang.String.format(");
    }

    @Test
    public void checksIncludeLambdaBodiesAndMarkedCallbackTargets() {
        JavaClasses examples = new ClassFileImporter().importClasses(CallbackExamples.class);
        Set<String> violations = violations(examples);
        assertViolation(violations, "CallbackExamples.lambda", "java.lang.String.format(");
        assertViolation(violations, "CallbackExamples.savedText", "java.lang.String.replaceAll(");
    }

    @Test
    public void listInterfacesDoNotHideArrayBackedTraversalOrRemoval() {
        JavaClasses examples = new ClassFileImporter().importClasses(ListExamples.class);
        Set<String> violations = violations(examples);
        assertViolation(violations, "ListExamples.iterator", "java.util.List.iterator(");
        assertViolation(violations, "ListExamples.listIterator", "java.util.List.listIterator(");
        assertViolation(violations, "ListExamples.remove", "java.util.List.removeIf(");
        assertViolation(violations, "ListExamples.subList", "java.util.List.iterator(");
    }

    @Test
    public void textRulesCoverEachRegexAndFormattingEntryPoint() {
        JavaClasses examples = new ClassFileImporter().importClasses(MoreTextExamples.class);
        Set<String> violations = violations(examples);
        assertViolation(violations, "MoreTextExamples.matches", "java.lang.String.matches(");
        assertViolation(violations, "MoreTextExamples.replaceFirst",
                "java.lang.String.replaceFirst(");
        assertViolation(violations, "MoreTextExamples.patternMatches",
                "java.util.regex.Pattern.matches(");
        assertViolation(violations, "MoreTextExamples.split", "java.lang.String.split(");
        assertViolation(violations, "MoreTextExamples.patternSplit",
                "java.util.regex.Pattern.split(");
        assertViolation(violations, "MoreTextExamples.join", "java.lang.String.join(");
        assertViolation(violations, "MoreTextExamples.formatter", "java.util.Formatter.<init>(");
    }

    @Test
    public void streamRulesCoverAlternateEntryPoints() {
        JavaClasses examples = new ClassFileImporter().importClasses(StreamExamples.class);
        Set<String> violations = violations(examples);
        assertViolation(violations, "StreamExamples.parallel",
                "java.util.Collection.parallelStream(");
        assertViolation(violations, "StreamExamples.array", "java.util.Arrays.stream(");
        assertViolation(violations, "StreamExamples.primitive",
                "java.util.stream.IntStream.range(");
        assertViolation(violations, "StreamExamples.support",
                "java.util.stream.StreamSupport.stream(");
    }

    @Test
    public void copyingRulesCoverCollectionConversionsArraysAndEnumValues() {
        JavaClasses examples =
                new ClassFileImporter().importClasses(CopyExamples.class, Kind.class);
        Set<String> violations = violations(examples);
        assertViolation(violations, "CopyExamples.toArray", "java.util.Collection.toArray(");
        assertViolation(violations, "CopyExamples.typedArray", "java.util.Collection.toArray(");
        assertViolation(violations, "CopyExamples.cloneArray", ".clone(");
        assertViolation(violations, "CopyExamples.enumValues", ".values(");
        assertViolation(violations, "CopyExamples.characters", "java.lang.String.toCharArray(");
        assertViolation(violations, "CopyExamples.bytes", "java.lang.String.getBytes(");
    }

    private static void assertViolation(Set<String> violations, String origin, String target) {
        for (String violation : violations) {
            if (violation.contains(origin + "(") && violation.contains(target))
                return;
        }
        fail("Missing " + origin + " -> " + target + "\n" + String.join("\n", violations));
    }

    private static Set<String> violations(JavaClasses classes) {
        HotPathCheck check = new HotPathCheck(classes);
        for (JavaClass type : classes) {
            for (JavaMethod method : type.getMethods()) {
                if (method.isAnnotatedWith(HotPath.class))
                    check.enqueue(method);
            }
        }
        return check.violations();
    }

    private static final class HotPathCheck {
        private final Set<String> owners = new HashSet<>();
        private final Set<JavaCodeUnit> visited = new HashSet<>();
        private final Deque<JavaAccess<?>> pending = new ArrayDeque<>();

        HotPathCheck(JavaClasses classes) {
            for (JavaClass type : classes)
                owners.add(type.getName());
        }

        void enqueue(JavaCodeUnit method) {
            if (!owners.contains(method.getOwner().getName()) || !visited.add(method))
                return;
            pending.addAll(method.getAccessesFromSelf());
            if (!(method instanceof JavaMethod)
                    || method.getModifiers().contains(JavaModifier.STATIC)
                    || method.getModifiers().contains(JavaModifier.PRIVATE)
                    || method.getModifiers().contains(JavaModifier.FINAL))
                return;
            for (JavaClass subtype : method.getOwner().getAllSubclasses()) {
                if (!owners.contains(subtype.getName()))
                    continue;
                for (JavaMethod implementation : subtype.getAllMethods()) {
                    if (implementation.getName().equals(method.getName())
                            && implementation.getRawParameterTypes()
                                    .equals(method.getRawParameterTypes())
                            && !implementation.getModifiers().contains(JavaModifier.STATIC)
                            && !implementation.getModifiers().contains(JavaModifier.PRIVATE))
                        enqueue(implementation);
                }
            }
        }

        Set<String> violations() {
            Set<String> violations = new TreeSet<>();
            while (!pending.isEmpty()) {
                JavaAccess<?> access = pending.removeFirst();
                if (!(access instanceof JavaMethodCall || access instanceof JavaMethodReference
                        || access instanceof JavaConstructorCall
                        || access instanceof JavaConstructorReference))
                    continue;
                AccessTarget target = access.getTarget();
                String reason = performanceReason(target);
                if (reason != null)
                    violations.add(access.getDescription() + ". " + reason);
                target.resolveMember().ifPresent(member -> {
                    if (member instanceof JavaCodeUnit)
                        enqueue((JavaCodeUnit) member);
                });
            }
            return violations;
        }
    }

    private static String performanceReason(AccessTarget target) {
        JavaClass owner = target.getOwner();
        String name = target.getName();
        if (owner.isAssignableTo(List.class) && !owner.isAssignableTo(AbstractSequentialList.class)
                && (name.equals("iterator") || name.equals("listIterator")))
            return "Use indexed traversal for array-backed lists in hot paths.";
        if (owner.isAssignableTo(List.class) && !owner.isAssignableTo(AbstractSequentialList.class)
                && name.equals("removeIf"))
            return "Use in-place removal. Java 8 ArrayList.removeIf creates a BitSet.";
        if (owner.isAssignableTo(Collection.class) && name.equals("toArray")
                || owner.isArray() && name.equals("clone")
                || owner.isEnum() && name.equals("values"))
            return "Cache the array instead of copying it in hot paths.";
        if (owner.getPackageName().equals("java.util.stream")
                || owner.isAssignableTo(Collection.class)
                        && (name.equals("stream") || name.equals("parallelStream"))
                || owner.getName().equals("java.util.Arrays") && name.equals("stream"))
            return "Use direct traversal in hot paths.";
        if (owner.getName().equals("java.lang.String")
                && (name.equals("format") || name.equals("join"))
                || (owner.isAssignableTo(Formatter.class) || owner.isAssignableTo(Format.class))
                        && (name.equals("<init>") || name.equals("format")))
            return "Build display text when its values change.";
        if (owner.getName().equals("java.lang.String")
                && (name.equals("split") || name.equals("toCharArray") || name.equals("getBytes"))
                || owner.getName().equals("java.util.regex.Pattern") && name.equals("split"))
            return "Parse or copy text when its values change, not in hot paths.";
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
        private static final Kind[] KINDS = Kind.values();
        private static final String[] NAMES =
                Collections.singletonList("one").toArray(new String[0]);

        @HotPath
        static int sum(String[] array, Map<String, String> map, Set<String> set,
                LinkedList<String> linked, AbstractSequentialList<String> sequential) {
            int length = 0;
            for (String value : array)
                length += value.length();
            for (String value : map.values())
                length += value.length();
            for (String value : set)
                length += value.length();
            for (String value : linked)
                length += value.length();
            for (String value : sequential)
                length += value.length();
            return length;
        }

        @HotPath
        static boolean cachedPattern(String value) {
            return PATTERN.matcher(value).matches();
        }

        @HotPath
        static int cachedArrays() {
            return KINDS.length + NAMES.length;
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
    private interface TextSource {
        String text();
    }
    private static class HiddenBase {
        @HotPath
        static String text() {
            return "base";
        }

        @HotPath
        @SuppressWarnings("PMD.UnusedPrivateMethod")
        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", matchType = EXACT)
        private String value() {
            return "base";
        }
    }
    private static final class HiddenChild extends HiddenBase {
        @SuppressFBWarnings(value = "HSM_HIDING_METHOD", matchType = EXACT)
        static String text() {
            return String.format("%s", "hidden");
        }

        String value() {
            return String.format("%s", "unrelated");
        }
    }
    private static final class InterfaceText implements TextSource {
        @Override
        public String text() {
            return String.format("%s", "interface");
        }
    }
    private static class BaseText {
        String text() {
            return "base";
        }
    }
    private static final class OverriddenText extends BaseText {
        @Override
        String text() {
            return "override".replaceAll("[a-z]", "");
        }
    }
    private interface FrameTask {
        void run();
    }
    private static final class FormattingTask implements FrameTask {
        @Override
        @SuppressWarnings("PMD.UselessPureMethodCall")
        @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", matchType = EXACT)
        public void run() {
            String.format("%s", "callback");
        }
    }
    private static final class DispatchExamples {
        private final FrameTask task;

        DispatchExamples(FrameTask task) {
            this.task = task;
        }

        @HotPath
        String text(TextSource source, BaseText base) {
            task.run();
            return source.text() + base.text();
        }
    }
    private static final class ConcreteCallback {
        @HotPath
        static void run(FormattingTask task) {
            task.run();
        }
    }
    private static final class ExternalCalls {
        @HotPath
        static void run(Runnable task) {
            task.run();
        }

        @HotPath
        static Iterator<String> iterator(Iterable<String> values) {
            return values.iterator();
        }
    }
    private static final class ColdWorker extends Thread {
        @Override
        @SuppressWarnings("PMD.UselessPureMethodCall")
        @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", matchType = EXACT)
        public void run() {
            String.format("%s", "worker");
        }
    }
    private static final class ColdIterable implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return Collections.singletonList("cold").iterator();
        }
    }
    private interface HotTextSource {
        @HotPath
        String text();
    }
    private static final class HotText implements HotTextSource {
        @Override
        public String text() {
            return String.format("%s", "contract");
        }
    }
    private static class TextParent {
        public String text() {
            return String.format("%s", "inherited");
        }
    }
    private static final class InheritedText extends TextParent implements HotTextSource {
    }
    private static final class CallbackExamples {
        private static final Supplier<String> TEXT = CallbackExamples::savedText;

        @HotPath
        static String lambda() {
            Supplier<String> text = () -> String.format("%s", "lambda");
            return text.get();
        }

        @HotPath
        static String text() {
            return TEXT.get();
        }

        @HotPath
        private static String savedText() {
            return "callback".replaceAll("[a-z]", "");
        }
    }
    private static final class ListExamples {
        @HotPath
        static Iterator<String> iterator(List<String> values) {
            return values.iterator();
        }

        @HotPath
        static Iterator<String> listIterator(List<String> values) {
            return values.listIterator();
        }

        @HotPath
        static void remove(List<String> values) {
            values.removeIf(String::isEmpty);
        }

        @HotPath
        static Iterator<String> subList(ArrayList<String> values) {
            return values.subList(0, 1).iterator();
        }
    }
    private static final class MoreTextExamples {
        private static final Pattern PATTERN = Pattern.compile("\\s+");

        @HotPath
        static boolean matches(String value) {
            return value.matches("[a-z]+");
        }

        @HotPath
        static String replaceFirst(String value) {
            return value.replaceFirst("[a-z]", "");
        }

        @HotPath
        static boolean patternMatches(String value) {
            return Pattern.matches("[a-z]+", value);
        }

        @HotPath
        static String[] split(String value) {
            return value.split("\\s+");
        }

        @HotPath
        static String[] patternSplit(String value) {
            return PATTERN.split(value);
        }

        @HotPath
        static String join(List<String> values) {
            return String.join(", ", values);
        }

        @HotPath
        @SuppressWarnings("PMD.RelianceOnDefaultCharset")
        static Formatter formatter(String value) {
            return new Formatter().format("%s", value);
        }
    }
    private static final class StreamExamples {
        @HotPath
        static long parallel(Collection<String> values) {
            return values.parallelStream().count();
        }

        @HotPath
        static long array(String[] values) {
            return Arrays.stream(values).count();
        }

        @HotPath
        static int primitive(int end) {
            return IntStream.range(0, end).sum();
        }

        @HotPath
        static long support(Collection<String> values) {
            return StreamSupport.stream(values.spliterator(), false).count();
        }
    }
    private enum Kind {
        FIRST, SECOND
    }
    private static final class CopyExamples {
        @HotPath
        static Object[] toArray(Collection<String> values) {
            return values.toArray();
        }

        @HotPath
        static String[] typedArray(Collection<String> values) {
            return values.toArray(new String[0]);
        }

        @HotPath
        static int[] cloneArray(int[] values) {
            return values.clone();
        }

        @HotPath
        static Kind[] enumValues() {
            return Kind.values();
        }

        @HotPath
        static char[] characters(String value) {
            return value.toCharArray();
        }

        @HotPath
        static byte[] bytes(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }
}
