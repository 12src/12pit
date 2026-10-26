import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import net.fabricmc.loom.api.mappings.layered.MappingContext
import net.fabricmc.loom.api.mappings.layered.MappingLayer
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec
import net.fabricmc.loom.api.mappings.layered.spec.MappingsSpec
import net.fabricmc.loom.api.processor.MinecraftJarProcessor
import net.fabricmc.loom.api.processor.ProcessorContext
import net.fabricmc.loom.api.processor.SpecContext
import net.fabricmc.loom.util.FileSystemUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingDstNsReorder
import net.fabricmc.mappingio.adapter.MappingNsCompleter
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.ornithemc.ploceus.mcp.io.McpModernFiles
import net.ornithemc.ploceus.mcp.io.McpReader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

plugins {
    java
    id("fabric-loom")
    id("ploceus")
}

val sharedProperties = Properties().apply {
    file("../gradle.properties").inputStream().use { load(it) }
}
val modId = sharedProperties.getProperty("modId")
val modName = sharedProperties.getProperty("modName")
val minecraftVersion = sharedProperties.getProperty("minecraftVersion")

group = sharedProperties.getProperty("modGroup")
version = sharedProperties.getProperty("modVersion")

java { toolchain.languageVersion.set(JavaLanguageVersion.of(8)) }

base { archivesName.set("$modName-ornithe") }

loom {
    mixin {
        useLegacyMixinAp.set(true)
        defaultRefmapName.set("mixins.$modId.refmap.json")
    }
    runs { remove(getByName("server")) }
    addMinecraftJarProcessor(McpGuiExceptions::class.java)
}

repositories {
    mavenCentral()
    maven("https://maven.cloverclient.com/releases") {
        content { includeGroup("pl.tomgirl") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(ploceus.layeredMappings {
        addLayer(SharedMcpMappingsSpec(
            FileSpec.create("net.ornithemc:calamus-intermediary-gen2:$minecraftVersion:v2"),
            FileSpec.create("de.oceanlabs.mcp:mcp:$minecraftVersion:srg@zip"),
            FileSpec.create("de.oceanlabs.mcp:mcp_stable:${sharedProperties.getProperty("mappingsVersion")}@zip"),
        ))
    })
    modImplementation("net.fabricmc:fabric-loader:${sharedProperties.getProperty("fabricLoaderVersion")}")
    modCompileOnly("pl.tomgirl:pylon:${sharedProperties.getProperty("pylonVersion")}") { isTransitive = false }
    compileOnly("com.google.code.gson:gson:${sharedProperties.getProperty("minecraftGsonVersion")}")
}

val buildConfigProperties = mapOf(
    "modId" to modId,
    "modName" to modName,
    "modVersion" to version,
    "releaseBuild" to providers.gradleProperty("releaseBuild").getOrElse("false").toBoolean(),
)
val generatedBuildConfigDirectory = layout.buildDirectory.dir("generated/sources/buildConfig/java/main")
val generateBuildConfig = tasks.register<Sync>("generateBuildConfig") {
    inputs.properties(buildConfigProperties)
    filteringCharset = "UTF-8"
    from("../src/main/templates") {
        include("**/*.java.template")
        expand(buildConfigProperties)
        rename { it.removeSuffix(".template") }
    }
    into(generatedBuildConfigDirectory)
}

sourceSets.main {
    java.setSrcDirs(listOf("../src/main/java", "../src/ornithe/java"))
    java.srcDir(generatedBuildConfigDirectory)
    resources.setSrcDirs(listOf("../src/main/resources", "../src/ornithe/resources"))
}

tasks.compileJava { dependsOn(generateBuildConfig) }
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

val generateBuildInfo = tasks.register<WriteProperties>("generateBuildInfo") {
    destinationFile.set(layout.buildDirectory.file("generated/resources/buildInfo/build.properties"))
    property("gitCommit", providers.environmentVariable("GITHUB_SHA").orElse(""))
}

val npm = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"
val installWebUi = tasks.register<Exec>("installWebUi") {
    inputs.files("../web-ui/package.json", "../web-ui/package-lock.json")
    outputs.file("../web-ui/node_modules/.package-lock.json")
    commandLine(npm, "ci", "--prefix", "../web-ui")
}
val buildWebUi = tasks.register<Exec>("buildWebUi") {
    dependsOn(installWebUi)
    inputs.files(
        fileTree("../web-ui/src"), "../web-ui/index.html", "../web-ui/vite.config.ts",
        "../web-ui/tsconfig.json", "../web-ui/package.json", "../web-ui/package-lock.json",
    )
    outputs.dir("../web-ui/dist")
    commandLine(npm, "run", "build", "--prefix", "../web-ui")
}

tasks.processResources {
    dependsOn(buildWebUi)
    from("../web-ui/dist") { into("assets/pit12/web") }
    from(generateBuildInfo) { into("assets/pit12") }
    val properties = mapOf(
        "version" to project.version,
        "mcversion" to minecraftVersion,
        "modId" to modId,
        "modName" to modName,
        "basePackage" to project.group,
    )
    inputs.properties(properties)
    filteringCharset = "UTF-8"
    filesMatching(listOf("fabric.mod.json", "mixins.$modId.json")) { expand(properties) }
}

tasks.jar {
    archiveVersion.set("")
    from("../LICENSE")
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveVersion.set("")
    archiveClassifier.set("")
}

tasks.named<JavaExec>("runClient") {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
}

data class SharedMcpMappingsSpec(
    val intermediary: FileSpec,
    val srg: FileSpec,
    val mcp: FileSpec,
) : MappingsSpec<SharedMcpMappingsLayer> {
    override fun createLayer(context: MappingContext) = SharedMcpMappingsLayer(
        intermediary.get(context), srg.get(context), mcp.get(context),
    )
}

class SharedMcpMappingsLayer(
    private val intermediary: Path,
    private val srg: Path,
    private val mcp: Path,
) : MappingLayer {
    override fun getSourceNamespace() = MappingsNamespace.INTERMEDIARY

    override fun visit(visitor: MappingVisitor) {
        McpModernFiles(intermediary, srg, mcp).use { files ->
            // Forge keeps SRG names for members that MCP has not named yet.
            McpReader.read(files).accept(MappingSourceNsSwitch(
                MappingNsCompleter(MappingDstNsReorder(visitor, "named"), mapOf("named" to "srg")),
                "intermediary", true,
            ))
        }
    }
}

abstract class McpGuiExceptions : MinecraftJarProcessor<McpGuiExceptions.Spec> {
    data class Spec(val methods: Set<String>) : MinecraftJarProcessor.Spec

    override fun getName() = "pit12:mcp_gui_exceptions"

    override fun buildSpec(context: SpecContext) = Spec(setOf(
        "handleInput", "handleMouseInput", "handleKeyboardInput", "mouseClicked", "keyTyped",
        "actionPerformed",
    ))

    override fun processJar(jar: Path, spec: Spec, context: ProcessorContext) {
        FileSystemUtil.getJarFileSystem(jar).use { archive ->
            val screen = archive.getPath("net/minecraft/client/gui/GuiScreen.class")
            val reader = ClassReader(Files.readAllBytes(screen))
            val writer = ClassWriter(reader, 0)
            reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
                override fun visitMethod(access: Int, name: String, descriptor: String,
                    signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                    // Forge restores these declarations from MCP's joined.exc.
                    val declared = if (name in spec.methods)
                        (exceptions.orEmpty().toList() + "java/io/IOException").distinct().toTypedArray()
                    else exceptions
                    return super.visitMethod(access, name, descriptor, signature, declared)
                }
            }, 0)
            Files.write(screen, writer.toByteArray())
        }
    }
}
