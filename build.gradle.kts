import dev.architectury.pack200.java.Pack200Adapter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import net.fabricmc.loom.task.RemapJarTask
import org.apache.commons.lang3.SystemUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode

plugins {
    java
    jacoco
    id("com.diffplug.spotless")
    id("gg.essential.loom")
    id("dev.architectury.architectury-pack200")
    id("com.gradleup.shadow")
}

val modId = providers.gradleProperty("modId").get()
val modName = providers.gradleProperty("modName").get()
val modVersion = providers.gradleProperty("modVersion").get()
val modGroup = providers.gradleProperty("modGroup").get()
val minecraftVersion = providers.gradleProperty("minecraftVersion").get()
val forgeVersion = providers.gradleProperty("forgeVersion").get()
val mappingsVersion = providers.gradleProperty("mappingsVersion").get()
val minecraftGsonVersion = providers.gradleProperty("minecraftGsonVersion").get()
val mixinRuntimeVersion = providers.gradleProperty("mixinRuntimeVersion").get()
val mixinProcessorVersion = providers.gradleProperty("mixinProcessorVersion").get()
val devAuthVersion = providers.gradleProperty("devAuthVersion").get()
val archUnitVersion = providers.gradleProperty("archUnitVersion").get()
val oneConfigVersion = providers.gradleProperty("oneConfigVersion").get()
val ktfmtVersion = providers.gradleProperty("ktfmtVersion").get()
val licenseHeaderPath = providers.gradleProperty("licenseHeaderPath").get()
val eclipseFormatterConfigPath = providers.gradleProperty("eclipseFormatterConfigPath").get()
val mainJavaDirectory = file("src/main/java")
// ArchitectureTest checks that these packages contain only Mixins.
val mixinConfigurations =
    fileTree(mainJavaDirectory) {
            val basePath = modGroup.replace('.', '/')
            include("$basePath/platform/mixin/**/*.java", "$basePath/feature/*/*/mixin/**/*.java")
        }
        .files
        .map { it.relativeTo(mainJavaDirectory).invariantSeparatorsPath.removeSuffix(".java").replace('/', '.') }
        .sortedWith(compareBy<String> { it.startsWith("$modGroup.feature.") }.thenBy { it })
        .groupBy { it.substringBefore(".mixin.") + ".mixin" }
        .map { (mixinPackage, mixins) ->
            val configName =
                if (mixinPackage == "$modGroup.platform.mixin") "mixins.$modId.json"
                else "mixins.$modId.${mixinPackage.removePrefix("$modGroup.feature.").removeSuffix(".mixin")}.json"
            configName to
                mapOf(
                    "mixinPackage" to mixinPackage,
                    "mixinClasses" to mixins.joinToString(",\n") { "    \"${it.removePrefix("$mixinPackage.")}\"" },
                )
        }
        .toMap()

group = modGroup

version = modVersion

java { toolchain.languageVersion.set(JavaLanguageVersion.of(8)) }

val legacyJavaLauncher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) }

loom {
    runs {
        named("client") {
            programArgs("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")

            if (SystemUtils.IS_OS_MAC_OSX) {
                vmArgs.remove("-XstartOnFirstThread")
            }
        }
        remove(getByName("server"))
    }

    forge {
        pack200Provider.set(Pack200Adapter())
        accessTransformer("src/main/resources/META-INF/at.cfg")
        mixinConfigurations.keys.forEach { mixinConfig(it) }
    }

    mixin {
        defaultRefmapName.set("mixins.$modId.refmap.json")
        // Mixin needs the legacy processor to read MCP mappings here.
        useLegacyMixinAp.set(true)
    }
}

val buildConfigProperties =
    mapOf(
        "modId" to modId,
        "modName" to modName,
        "modVersion" to modVersion,
        "releaseBuild" to providers.gradleProperty("release").isPresent,
    )
val generatedBuildConfigDirectory = layout.buildDirectory.dir("generated/sources/buildConfig/java/main")
val generateBuildConfig =
    tasks.register<Sync>("generateBuildConfig") {
        inputs.properties(buildConfigProperties)
        filteringCharset = "UTF-8"

        from("src/main/templates") {
            include("**/*.java.template")
            expand(buildConfigProperties)
            rename { it.removeSuffix(".template") }
        }
        into(generatedBuildConfigDirectory)
    }

val generateMixinConfigs =
    tasks.register<Sync>("generateMixinConfigs") {
        val properties = mapOf("modId" to modId, "basePackage" to modGroup)
        inputs.properties(properties)
        inputs.property("mixinConfigurations", mixinConfigurations)
        filteringCharset = "UTF-8"

        mixinConfigurations.forEach { (configName, configuration) ->
            from("src/main/templates/mixins.json.template") {
                expand(properties + configuration)
                rename { configName }
            }
        }
        into(layout.buildDirectory.dir("generated/mixinConfigs"))
    }

sourceSets.main {
    java.srcDir(generatedBuildConfigDirectory)
    resources.srcDir(generateMixinConfigs)
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}

val generateBuildInfo =
    tasks.register<WriteProperties>("generateBuildInfo") {
        destinationFile.set(layout.buildDirectory.file("generated/resources/buildInfo/build.properties"))
        property("gitCommit", providers.environmentVariable("GITHUB_SHA").orElse(""))
    }

tasks.compileJava { dependsOn(generateBuildConfig) }

// BootstrapLayoutTest reads the comments in ClientBootstrap.java.
tasks.test {
    inputs.file("src/main/java/pit12/bootstrap/ClientBootstrap.java")
    val logConfig = temporaryDir.resolve("log4j2.xml")
    systemProperty("log4j.configurationFile", logConfig.absolutePath)
    doFirst {
        logConfig.writeText(
            """
            <Configuration status="WARN">
                <Appenders>
                    <Console name="Console" target="SYSTEM_OUT">
                        <PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level]: %msg%n" />
                    </Console>
                </Appenders>
                <Loggers>
                    <Root level="info"><AppenderRef ref="Console" /></Root>
                </Loggers>
            </Configuration>
            """
                .trimIndent()
        )
    }
}

tasks.jacocoTestReport { reports { xml.required = true } }

repositories {
    mavenCentral()
    maven("https://repo.polyfrost.org/releases") {
        content { includeGroup("cc.polyfrost") }
    }
    maven("https://repo.spongepowered.org/maven/")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

val shaded =
    configurations.create("shaded") {
        isCanBeConsumed = false
        configurations.implementation.get().extendsFrom(this)
    }

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("de.oceanlabs.mcp:mcp_stable:$mappingsVersion")
    forge("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    compileOnly("com.google.code.gson:gson:$minecraftGsonVersion")
    // full-dev gives us the registry we need to remove the view on shutdown.
    compileOnly("cc.polyfrost:oneconfig-1.8.9-forge:$oneConfigVersion:full-dev") { isTransitive = false }
    shaded("org.spongepowered:mixin:$mixinRuntimeVersion") { isTransitive = false }
    annotationProcessor("org.spongepowered:mixin:$mixinProcessorVersion:processor")

    runtimeOnly("me.djtheredstoner:DevAuth-forge-legacy:$devAuthVersion")

    testImplementation("com.google.code.gson:gson:$minecraftGsonVersion")
    testImplementation("com.tngtech.archunit:archunit-junit4:$archUnitVersion")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Aquiet")
}

val licenseText = file(licenseHeaderPath).readLines().joinToString("\n")
val blockLicenseHeader =
    "/*\n" + licenseText.lines().joinToString("\n") { if (it.isEmpty()) " *" else " * $it" } + "\n */"
val htmlLicenseHeader = "<!--\n$licenseText\n-->"

spotless {
    java {
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        eclipse().configFile(eclipseFormatterConfigPath)
        importOrder("\\#", "")
        removeUnusedImports()
        forbidWildcardImports()
        licenseHeader(blockLicenseHeader).updateYearWithLatest(false)
    }

    kotlin {
        target("src/main/kotlin/**/*.kt")
        ktfmt(ktfmtVersion).kotlinlangStyle().configure {
            it.setMaxWidth(120)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
        }
        licenseHeader(blockLicenseHeader).updateYearWithLatest(false)
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktfmt(ktfmtVersion).kotlinlangStyle().configure {
            it.setMaxWidth(120)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
        }
    }

    format("webUiCode") {
        target("web-ui/src/**/*.ts", "web-ui/src/**/*.css", "web-ui/vite.config.ts")
        licenseHeader(blockLicenseHeader, "(?m)^(?!/\\*|//)\\S").updateYearWithLatest(false)
    }

    format("webUiMarkup") {
        target("web-ui/src/**/*.vue", "web-ui/index.html")
        licenseHeader(htmlLicenseHeader, "(?m)^<(?:!doctype|script|template|style)\\b").updateYearWithLatest(false)
    }
}

tasks.withType<Jar> {
    archiveBaseName.set(modName)
    archiveVersion.set("")
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = mixinConfigurations.keys.joinToString(",")
        this["FMLAT"] = "at.cfg"
    }
}

val npm = if (SystemUtils.IS_OS_WINDOWS) "npm.cmd" else "npm"
val installWebUi =
    tasks.register<Exec>("installWebUi") {
        inputs.files("web-ui/package.json", "web-ui/package-lock.json")
        outputs.file("web-ui/node_modules/.package-lock.json")
        commandLine(npm, "ci", "--prefix", "web-ui")
    }
val buildWebUi =
    tasks.register<Exec>("buildWebUi") {
        dependsOn(installWebUi)
        inputs.files(
            fileTree("web-ui/src"),
            "web-ui/index.html",
            "web-ui/vite.config.ts",
            "web-ui/tsconfig.json",
            "web-ui/package.json",
            "web-ui/package-lock.json",
        )
        outputs.dir("web-ui/dist")
        commandLine(npm, "run", "build", "--prefix", "web-ui")
    }

tasks.named<JavaExec>("runClient") {
    javaLauncher.set(legacyJavaLauncher)
    dependsOn(buildWebUi)
}

tasks.named("generateDLIConfig") {
    notCompatibleWithConfigurationCache("Loom reads project state when generating the launch configuration.")
}

tasks.withType<JavaExec>().configureEach {
    notCompatibleWithConfigurationCache("Loom and IDE launch tasks use project state at execution time.")
}

tasks.processResources {
    mustRunAfter(buildWebUi)
    from("web-ui/dist") { into("assets/pit12/web") }
    from(generateBuildInfo) { into("assets/pit12") }
    val properties =
        mapOf(
            "version" to project.version,
            "mcversion" to minecraftVersion,
            "modId" to modId,
            "modName" to modName,
        )

    inputs.properties(properties)
    filteringCharset = "UTF-8"

    filesMatching("mcmod.info") { expand(properties) }
}

val remapJar =
    tasks.named<RemapJarTask>("remapJar") {
        dependsOn(tasks.shadowJar)
        archiveBaseName.set(modName)
        archiveVersion.set("")
        archiveClassifier.set("")
        inputFile.set(tasks.shadowJar.get().archiveFile)
    }

tasks.jar { enabled = false }

tasks.shadowJar {
    dependsOn(buildWebUi)
    destinationDirectory.set(layout.buildDirectory.dir("tmp/shadowJar"))
    archiveClassifier.set("dev-shadow")
    configurations = listOf(shaded)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(rootProject.file("LICENSE"))

    exclude("LICENSE.txt")
    exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF")

    val hotPath = "pit12/shared/performance/HotPath"
    exclude("$hotPath.class")
    // Tests read the original classes. Strip the marker only from the packaged copy.
    doLast {
        val archive = archiveFile.get().asFile
        val stripped = temporaryDir.resolve("stripped.jar")
        ZipFile(archive).use { source ->
            ZipOutputStream(stripped.outputStream().buffered()).use { output ->
                for (entry in source.entries().asSequence()) {
                    output.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    source.getInputStream(entry).use { input ->
                        if (entry.name.startsWith("pit12/") && entry.name.endsWith(".class")) {
                            var bytes = input.readBytes()
                            val node = ClassNode()
                            ClassReader(bytes).accept(node, 0)
                            var changed = false
                            for (method in node.methods) {
                                if (method.invisibleAnnotations?.removeIf { it.desc == "L$hotPath;" } == true) {
                                    changed = true
                                }
                            }
                            if (changed) {
                                val writer = ClassWriter(0)
                                node.accept(writer)
                                bytes = writer.toByteArray()
                            }
                            output.write(bytes)
                        } else {
                            input.copyTo(output)
                        }
                    }
                    output.closeEntry()
                }
            }
        }
        Files.move(stripped.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

tasks.assemble { dependsOn(remapJar) }
