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

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
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
import java.io.BufferedReader;
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
                    .resideInAnyPackage("pit12", "pit12.bootstrap..", "pit12.shared.build..",
                            "java..", "net.minecraftforge.fml.common..")
                    .because("the entry point only connects Forge to Bootstrap and reads "
                            + "build constants");
    @ArchTest
    public static final ArchRule TOP_LEVEL_DEPENDENCIES = layeredArchitecture()
            .consideringOnlyDependenciesInLayers().layer("Application").definedBy("pit12")
            .layer("Bootstrap").definedBy("pit12.bootstrap..").layer("Platform")
            .definedBy("pit12.platform..", "pit12.feature.*.*.mixin..").layer("Runtime")
            .definedBy("pit12.runtime..").layer("Feature")
            .definedBy(resideInAPackage("pit12.feature..")
                    .and(resideOutsideOfPackage("pit12.feature.*.*.mixin..")))
            .layer("Shared").definedBy("pit12.shared..").whereLayer("Application")
            .mayOnlyAccessLayers("Bootstrap").whereLayer("Bootstrap")
            .mayOnlyAccessLayers("Platform", "Runtime", "Feature", "Shared").whereLayer("Platform")
            .mayOnlyAccessLayers("Runtime", "Feature", "Shared").whereLayer("Runtime")
            .mayOnlyAccessLayers("Shared").whereLayer("Feature")
            .mayOnlyAccessLayers("Runtime", "Shared").whereLayer("Shared").mayNotAccessAnyLayer();
    @ArchTest
    public static final ArchRule COMMAND_RUNTIME_MUST_NOT_DEPEND_ON_FORGE =
            noClasses().that().resideInAPackage("pit12.runtime.command..").should()
                    .dependOnClassesThat().resideInAPackage("net.minecraftforge..")
                    .because("Forge command registration belongs to the platform adapter");
    @ArchTest
    public static final ArchRule WEB_SERVER_DEPENDENCIES =
            classes().that().haveFullyQualifiedName("pit12.feature.client.webui.WebUiServer")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("pit12.feature.client.webui..", "java..", "com.google..",
                            "com.sun.net.httpserver..", "net.minecraft.client..")
                    .because("business endpoints belong to registered API adapters");
    @ArchTest
    public static final ArchRule EXTERNAL_APIS_BELONG_TO_PLATFORM = noClasses().that()
            .resideInAnyPackage("pit12.bootstrap..", "pit12.runtime..", "pit12.feature..",
                    "pit12.shared..")
            .and().resideOutsideOfPackage("pit12.feature.*.*.mixin..").should()
            .dependOnClassesThat()
            .resideOutsideOfPackages("pit12..", "java..", "javax..", "com.sun.net.httpserver..",
                    "net.minecraft..", "net.minecraftforge..", "com.mojang..", "com.google..",
                    "org.lwjgl..", "org.apache.http..")
            .because("optional client and native API access belongs to platform adapters");

    @ArchTest
    public static void MODULES_MUST_BE_FREE_OF_CYCLES(JavaClasses classes) {
        slices().matching("pit12.(*).(*)..").should().beFreeOfCycles()
                .check(classes.that(resideOutsideOfPackage("pit12.platform.mixin..")
                        .and(resideOutsideOfPackage("pit12.feature.."))));
        slices().matching("pit12.feature.(*).(*)..").should().beFreeOfCycles().check(classes);
    }

    @ArchTest
    public static final ArchRule CLIENT_LIFECYCLE_OWNERS =
            classes().that().areAssignableTo("pit12.shared.lifecycle.ClientLifecycle").should()
                    .resideInAnyPackage("pit12.platform..", "pit12.runtime..", "pit12.feature..",
                            "pit12.shared.lifecycle..");
    @ArchTest
    public static final ArchRule FEATURES_MUST_ONLY_USE_OTHER_FEATURE_APIS =
            classes().that().resideInAPackage("pit12.feature..").should(onlyUseOtherFeatureApis())
                    .because("a feature's internal classes belong to that feature");
    @ArchTest
    public static final ArchRule MIXINS_MUST_RESIDE_IN_MIXIN_PACKAGES =
            classes().that().areAnnotatedWith("org.spongepowered.asm.mixin.Mixin").should()
                    .resideInAnyPackage("pit12.platform.mixin..", "pit12.feature.*.*.mixin..");
    @ArchTest
    public static final ArchRule MIXIN_PACKAGES_MUST_ONLY_CONTAIN_MIXINS = classes().that()
            .resideInAnyPackage("pit12.platform.mixin..", "pit12.feature.*.*.mixin..").should()
            .beAnnotatedWith("org.spongepowered.asm.mixin.Mixin")
            .because("ordinary classes in the configured Mixin tree cannot be loaded directly");

    @ArchTest
    public static void FEATURE_MIXINS_MUST_RESPECT_FEATURE_BOUNDARIES(JavaClasses classes) {
        Set<String> featureNames = new TreeSet<String>();
        for (JavaClass javaClass : classes) {
            String featureName = featureNameOf(javaClass);
            if (featureName != null
                    && !javaClass.isAnnotatedWith("org.spongepowered.asm.mixin.Mixin")) {
                featureNames.add(featureName);
            }
        }
        Set<String> violations = new TreeSet<String>();
        for (JavaClass javaClass : classes) {
            if (!javaClass.isAnnotatedWith("org.spongepowered.asm.mixin.Mixin")) {
                continue;
            }
            String declaredFeature = featureNameOf(javaClass);
            if (declaredFeature != null && !featureNames.contains(declaredFeature)) {
                violations.add(javaClass.getName() + " mirrors missing feature " + declaredFeature);
            }
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String dependencyFeature = featureNameOf(target);
                if (dependencyFeature == null || dependencyFeature.equals(declaredFeature)) {
                    continue;
                }
                if (declaredFeature == null) {
                    violations.add(javaClass.getName() + " depends on feature " + dependencyFeature
                            + " and requires a feature Mixin package");
                } else if (!isFeatureApi(target, dependencyFeature)) {
                    violations.add(javaClass.getName() + " depends on internal classes of feature "
                            + dependencyFeature);
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new AssertionError("Feature Mixin boundary mismatch: " + violations);
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
        int categoryEnd = packageName.indexOf('.', prefix.length());
        int separator = packageName.indexOf('.', categoryEnd + 1);
        return separator < 0 ? packageName.substring(prefix.length())
                : packageName.substring(prefix.length(), separator);
    }

    private static boolean isFeatureApi(JavaClass javaClass, String featureName) {
        String apiPackage = "pit12.feature." + featureName + ".api";
        String packageName = javaClass.getPackageName();
        return packageName.equals(apiPackage) || packageName.startsWith(apiPackage + ".");
    }

    private static Set<String> configuredMixins() {
        InputStream stream =
                ArchitectureTest.class.getClassLoader().getResourceAsStream("mixins.pit12.list");
        if (stream == null) {
            throw new AssertionError("Missing mixins.pit12.list on the test runtime classpath");
        }
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            Set<String> configuredMixins = new TreeSet<String>();
            String configName;
            while ((configName = reader.readLine()) != null) {
                addConfiguredMixins(configuredMixins, configName);
            }
            return configuredMixins;
        } catch (IOException failure) {
            throw new AssertionError("Cannot read mixins.pit12.list", failure);
        }
    }

    private static void addConfiguredMixins(Set<String> destination, String configName) {
        try (InputStream stream =
                ArchitectureTest.class.getClassLoader().getResourceAsStream(configName)) {
            if (stream == null) {
                throw new AssertionError(
                        "Missing " + configName + " on the test runtime classpath");
            }
            JsonObject config = new JsonParser()
                    .parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            String basePackage = config.get("package").getAsString();
            addConfiguredMixins(destination, config, basePackage, "mixins");
            addConfiguredMixins(destination, config, basePackage, "client");
            addConfiguredMixins(destination, config, basePackage, "server");
        } catch (IOException | RuntimeException failure) {
            throw new AssertionError("Cannot read " + configName, failure);
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
