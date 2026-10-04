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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.junit.runner.RunWith;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = "pit12", importOptions = DoNotIncludeTests.class)
public final class ArchitectureTest {
    @ArchTest
    public static final ArchRule CLASSES_MUST_USE_DEFINED_TOP_LEVEL_PACKAGES =
            classes().should().resideInAnyPackage("pit12", "pit12.bootstrap..", "pit12.platform..",
                    "pit12.runtime..", "pit12.feature..", "pit12.shared..");
    @ArchTest
    public static final ArchRule ROOT_PACKAGE_MUST_ONLY_CONTAIN_THE_MOD_ENTRY_POINT =
            classes().that().resideInAPackage("pit12").should().haveSimpleName("Pit12");
    @ArchTest
    public static final ArchRule PIT12_MUST_BE_THE_FORGE_MOD_ENTRY_POINT =
            classes().that().haveFullyQualifiedName("pit12.Pit12").should()
                    .beAnnotatedWith("net.minecraftforge.fml.common.Mod");
    @ArchTest
    public static final ArchRule FORGE_MOD_ENTRY_POINT_MUST_BE_UNIQUE =
            classes().that().areAnnotatedWith("net.minecraftforge.fml.common.Mod").should()
                    .haveFullyQualifiedName("pit12.Pit12");
    @ArchTest
    public static final ArchRule APPLICATION_ENTRY_POINT_DEPENDENCIES =
            classes().that().resideInAPackage("pit12").should().onlyDependOnClassesThat()
                    .resideInAnyPackage("pit12", "pit12.bootstrap..", "java..",
                            "net.minecraftforge.fml.common..")
                    .because("the entry point only passes Forge events to Bootstrap");
    @ArchTest
    public static final ArchRule TOP_LEVEL_DEPENDENCIES = layeredArchitecture()
            .consideringOnlyDependenciesInLayers().layer("Application").definedBy("pit12")
            .layer("Bootstrap").definedBy("pit12.bootstrap..").layer("Platform")
            .definedBy("pit12.platform..").layer("Runtime").definedBy("pit12.runtime..")
            .layer("Feature").definedBy("pit12.feature..").layer("Shared")
            .definedBy("pit12.shared..").whereLayer("Application").mayOnlyAccessLayers("Bootstrap")
            .whereLayer("Bootstrap").mayOnlyAccessLayers("Platform", "Runtime", "Feature", "Shared")
            .whereLayer("Platform").mayOnlyAccessLayers("Runtime", "Feature", "Shared")
            .whereLayer("Runtime").mayOnlyAccessLayers("Shared").whereLayer("Feature")
            .mayOnlyAccessLayers("Runtime", "Shared").whereLayer("Shared").mayNotAccessAnyLayer();
    @ArchTest
    public static final ArchRule COMMAND_RUNTIME_MUST_NOT_DEPEND_ON_FORGE =
            noClasses().that().resideInAPackage("pit12.runtime.command..").should()
                    .dependOnClassesThat().resideInAPackage("net.minecraftforge..")
                    .because("Forge command registration belongs to the platform adapter");
    @ArchTest
    public static final ArchRule MODULES_MUST_BE_FREE_OF_CYCLES =
            slices().matching("pit12.(*).(*)..").should().beFreeOfCycles();
    @ArchTest
    public static final ArchRule CLIENT_LIFECYCLE_OWNERS = classes().that()
            .areAssignableTo("pit12.shared.lifecycle.ClientLifecycle").should()
            .resideInAnyPackage("pit12.runtime..", "pit12.feature..", "pit12.shared.lifecycle..");
    @ArchTest
    public static final ArchRule FEATURES_MUST_ONLY_USE_OTHER_FEATURE_APIS =
            classes().that().resideInAPackage("pit12.feature..").should(onlyUseOtherFeatureApis())
                    .because("a feature's internal classes belong to that feature");
    @ArchTest
    public static final ArchRule MIXINS_MUST_RESIDE_IN_MIXIN_PACKAGES =
            classes().that().areAnnotatedWith("org.spongepowered.asm.mixin.Mixin").should()
                    .resideInAPackage("pit12.platform.mixin..");
    @ArchTest
    public static final ArchRule MIXIN_PACKAGES_MUST_ONLY_CONTAIN_MIXINS = classes().that()
            .resideInAPackage("pit12.platform.mixin..").should()
            .beAnnotatedWith("org.spongepowered.asm.mixin.Mixin")
            .because("ordinary classes in the configured Mixin tree cannot be loaded directly");

    @ArchTest
    public static void FEATURE_MIXINS_MUST_MIRROR_FEATURE_PACKAGES(JavaClasses classes) {
        Set<String> featureNames = new TreeSet<String>();
        for (JavaClass javaClass : classes) {
            String featureName = featureNameOf(javaClass);
            if (featureName != null) {
                featureNames.add(featureName);
            }
        }
        Set<String> violations = new TreeSet<String>();
        for (JavaClass javaClass : classes) {
            if (!javaClass.isAnnotatedWith("org.spongepowered.asm.mixin.Mixin")) {
                continue;
            }
            String declaredFeature = mixinFeatureNameOf(javaClass);
            Set<String> dependencies = new TreeSet<String>();
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                String dependencyFeature = featureNameOf(dependency.getTargetClass());
                if (dependencyFeature != null) {
                    dependencies.add(dependencyFeature);
                }
            }
            if (declaredFeature == null) {
                if (!dependencies.isEmpty()) {
                    violations.add(javaClass.getName() + " depends on feature(s) " + dependencies
                            + " outside platform.mixin.feature");
                }
                continue;
            }
            if (!featureNames.contains(declaredFeature)) {
                violations.add(javaClass.getName() + " mirrors missing feature " + declaredFeature);
            }
            dependencies.remove(declaredFeature);
            if (!dependencies.isEmpty()) {
                violations
                        .add(javaClass.getName() + " depends on other feature(s) " + dependencies);
            }
        }
        if (!violations.isEmpty()) {
            throw new AssertionError("Feature Mixin ownership mismatch: " + violations);
        }
    }

    @ArchTest
    public static void MIXINS_MUST_BE_REGISTERED(JavaClasses classes) {
        Set<String> implementedMixins = new TreeSet<String>();
        for (JavaClass javaClass : classes) {
            if (javaClass.isAnnotatedWith("org.spongepowered.asm.mixin.Mixin")) {
                implementedMixins.add(javaClass.getName());
            }
        }
        Set<String> configuredMixins = configuredMixins();
        if (implementedMixins.equals(configuredMixins)) {
            return;
        }
        Set<String> unregisteredMixins = new TreeSet<String>(implementedMixins);
        unregisteredMixins.removeAll(configuredMixins);
        Set<String> missingClasses = new TreeSet<String>(configuredMixins);
        missingClasses.removeAll(implementedMixins);
        throw new AssertionError("Mixin registration mismatch: unregistered=" + unregisteredMixins
                + ", missing classes=" + missingClasses);
    }

    private ArchitectureTest() {}

    private static ArchCondition<JavaClass> onlyUseOtherFeatureApis() {
        return new ArchCondition<JavaClass>("only use other features through their api packages") {
            @Override
            public void check(JavaClass source, ConditionEvents events) {
                String sourceFeature = featureNameOf(source);
                if (sourceFeature == null) {
                    return;
                }
                for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetFeature = featureNameOf(target);
                    if (targetFeature == null || sourceFeature.equals(targetFeature)
                            || isFeatureApi(target, targetFeature)) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(source, dependency.getDescription()));
                }
            }
        };
    }

    private static String featureNameOf(JavaClass javaClass) {
        String prefix = "pit12.feature.";
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        int separator = packageName.indexOf('.', prefix.length());
        return separator < 0 ? packageName.substring(prefix.length())
                : packageName.substring(prefix.length(), separator);
    }

    private static String mixinFeatureNameOf(JavaClass javaClass) {
        String prefix = "pit12.platform.mixin.feature.";
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        int separator = packageName.indexOf('.', prefix.length());
        return separator < 0 ? packageName.substring(prefix.length())
                : packageName.substring(prefix.length(), separator);
    }

    private static boolean isFeatureApi(JavaClass javaClass, String featureName) {
        String apiPackage = "pit12.feature." + featureName + ".api";
        String packageName = javaClass.getPackageName();
        return packageName.equals(apiPackage) || packageName.startsWith(apiPackage + ".");
    }

    private static Set<String> configuredMixins() {
        try (InputStream stream =
                ArchitectureTest.class.getClassLoader().getResourceAsStream("mixins.pit12.json")) {
            if (stream == null) {
                throw new AssertionError("Missing mixins.pit12.json on the test runtime classpath");
            }
            JsonObject config = new JsonParser()
                    .parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            String basePackage = config.get("package").getAsString();
            Set<String> configuredMixins = new TreeSet<String>();
            addConfiguredMixins(configuredMixins, config, basePackage, "mixins");
            addConfiguredMixins(configuredMixins, config, basePackage, "client");
            addConfiguredMixins(configuredMixins, config, basePackage, "server");
            return configuredMixins;
        } catch (IOException | RuntimeException failure) {
            throw new AssertionError("Cannot read mixins.pit12.json", failure);
        }
    }

    private static void addConfiguredMixins(Set<String> destination, JsonObject config,
            String basePackage, String listName) {
        JsonElement configured = config.get(listName);
        if (configured == null) {
            return;
        }
        for (JsonElement mixin : configured.getAsJsonArray()) {
            destination.add(basePackage + "." + mixin.getAsString());
        }
    }
}
