import org.apache.commons.lang3.SystemUtils

plugins {
    java
    id("com.diffplug.spotless")
    id("gg.essential.loom")
    id("dev.architectury.architectury-pack200")
    id("com.gradleup.shadow")
}

val modId: String by project
val modName: String by project
val modVersion: String by project
val modGroup: String by project
val minecraftVersion: String by project
val forgeVersion: String by project
val mappingsVersion: String by project
val minecraftGsonVersion: String by project
val mixinRuntimeVersion: String by project
val mixinProcessorVersion: String by project
val devAuthVersion: String by project
val archUnitVersion: String by project
val ktfmtVersion: String by project
val licenseHeaderPath: String by project
val eclipseFormatterConfigPath: String by project

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

    mixin { defaultRefmapName.set("mixins.$modId.refmap.json") }
}

tasks.named<JavaExec>("runClient") { javaLauncher.set(legacyJavaLauncher) }

sourceSets.main { output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory }) }

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

val shaded: Configuration by configurations.creating {
    isCanBeConsumed = false
    configurations.implementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("de.oceanlabs.mcp:mcp_stable:$mappingsVersion")
    forge("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    compileOnly("com.google.code.gson:gson:$minecraftGsonVersion")
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

spotless {
    java {
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        eclipse().configFile(eclipseFormatterConfigPath)
        importOrder("\\#", "")
        removeUnusedImports()
        forbidWildcardImports()
        licenseHeaderFile(licenseHeaderPath).updateYearWithLatest(false)
    }

    kotlin {
        target("src/main/kotlin/**/*.kt")
        ktfmt(ktfmtVersion).kotlinlangStyle().configure {
            it.setMaxWidth(120)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
        }
        licenseHeaderFile(licenseHeaderPath).updateYearWithLatest(false)
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktfmt(ktfmtVersion).kotlinlangStyle().configure {
            it.setMaxWidth(120)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
        }
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

tasks.processResources {
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

val remapJar by
    tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
        dependsOn(tasks.shadowJar)
        archiveBaseName.set(modId)
        archiveVersion.set("")
        archiveClassifier.set("")
        inputFile.set(tasks.shadowJar.get().archiveFile)
    }

tasks.jar { enabled = false }

tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("tmp/shadowJar"))
    archiveClassifier.set("dev-shadow")
    configurations = listOf(shaded)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(rootProject.file("LICENSE"))

    exclude("LICENSE.txt")
    exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF")
}

tasks.assemble.get().dependsOn(tasks.remapJar)
