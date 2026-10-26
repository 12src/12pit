pluginManagement {
    val sharedProperties = java.util.Properties().apply {
        file("../gradle.properties").inputStream().use { load(it) }
    }
    plugins {
        id("fabric-loom") version sharedProperties.getProperty("fabricLoomPluginVersion")
        id("ploceus") version sharedProperties.getProperty("ploceusPluginVersion")
        id("org.gradle.toolchains.foojay-resolver-convention") version sharedProperties.getProperty("foojayResolverPluginVersion")
    }
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.ornithemc.net/releases")
        gradlePluginPortal()
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") }

rootProject.name = "ornithe"
