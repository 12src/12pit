pluginManagement {
    val spotlessPluginVersion = providers.gradleProperty("spotlessPluginVersion").get()
    val architecturyLoomPluginVersion = providers.gradleProperty("architecturyLoomPluginVersion").get()
    val architecturyPack200PluginVersion = providers.gradleProperty("architecturyPack200PluginVersion").get()
    val shadowPluginVersion = providers.gradleProperty("shadowPluginVersion").get()
    val foojayResolverPluginVersion = providers.gradleProperty("foojayResolverPluginVersion").get()

    plugins {
        id("com.diffplug.spotless") version spotlessPluginVersion
        id("gg.essential.loom") version architecturyLoomPluginVersion
        id("dev.architectury.architectury-pack200") version architecturyPack200PluginVersion
        id("com.gradleup.shadow") version shadowPluginVersion
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayResolverPluginVersion
    }

    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.essential.gg/repository/maven-public/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "gg.essential.loom") {
                useModule("gg.essential:architectury-loom:$architecturyLoomPluginVersion")
            }
        }
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") }

rootProject.name = "12pit"
