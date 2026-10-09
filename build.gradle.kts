import net.fabricmc.loom.task.RemapJarTask
import org.apache.commons.lang3.SystemUtils

plugins {
    java
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
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("mixins.$modId.json")
    }

    mixin {
        defaultRefmapName.set("mixins.$modId.refmap.json")
        // Mixin needs Loom's legacy annotation processor to read MCP mappings on Forge 1.8.9.
        useLegacyMixinAp.set(true)
    }
}

val buildConfigProperties =
    mapOf(
        "modId" to modId,
        "modName" to modName,
        "modVersion" to modVersion,
        "releaseBuild" to providers.gradleProperty("releaseBuild").getOrElse("false").toBoolean(),
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

sourceSets.main {
    java.srcDir(generatedBuildConfigDirectory)
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}

val generateBuildInfo =
    tasks.register<WriteProperties>("generateBuildInfo") {
        destinationFile.set(layout.buildDirectory.file("generated/resources/buildInfo/build.properties"))
        property("gitCommit", providers.environmentVariable("GITHUB_SHA").orElse(""))
    }

tasks.compileJava { dependsOn(generateBuildConfig) }

// Layout checks need the section comments from the source file.
tasks.test {
    inputs.file("src/main/java/pit12/bootstrap/ClientBootstrap.java")
}

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
    // The development bundle exposes the registry used to unregister the view on shutdown.
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
        this["MixinConfigs"] = "mixins.$modId.json"
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
            "basePackage" to modGroup,
        )

    inputs.properties(properties)
    filteringCharset = "UTF-8"

    filesMatching(listOf("mcmod.info", "mixins.$modId.json")) { expand(properties) }
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
}

tasks.assemble { dependsOn(remapJar) }
