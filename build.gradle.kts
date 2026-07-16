import com.modrinth.minotaur.ModrinthExtension
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    kotlin("jvm") version "2.4.0" apply false
    id("net.fabricmc.fabric-loom") version "1.17.1" apply false
    id("com.modrinth.minotaur") version "2.9.0" apply false
}

fun Properties.required(name: String): String =
    getProperty(name)?.takeIf(String::isNotBlank)
        ?: error("gradle/targets.properties is missing $name")

val targetProperties = Properties().apply {
    rootProject.file("gradle/targets.properties").inputStream().use(::load)
}
val targetNames = targetProperties.required("targets")
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
val targets = targetNames.associateWith { name ->
    Target(
        minecraft = targetProperties.required("$name.minecraft"),
        fabricApi = targetProperties.required("$name.fabric_api"),
    )
}
val modVersion = providers.gradleProperty("mod_version").get()

allprojects {
    group = "org.hypixelskyblockmods.storagelens"
    version = modVersion

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
    }
}

subprojects {
    val target = targets[name] ?: return@subprojects
    val artifactVersion = "${rootProject.version}+mc${target.minecraft}"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "net.fabricmc.fabric-loom")
    apply(plugin = "com.modrinth.minotaur")

    version = artifactVersion

    extensions.configure<ModrinthExtension> {
        projectId.set(providers.environmentVariable("MODRINTH_PROJECT_ID"))
        versionNumber.set(artifactVersion)
        versionName.set("StorageLens $artifactVersion")
        versionType.set("release")
        uploadFile.set(tasks.named("jar"))
        gameVersions.set(listOf(target.minecraft))
        loaders.set(listOf("fabric"))
        dependencies {
            required.project("P7dR8mSH") // Fabric API
            required.project("Ha28R6CL") // Fabric Language Kotlin
        }
        changelog.set(
            providers.environmentVariable("MODRINTH_CHANGELOG")
                .orElse("See the corresponding GitHub release for changes."),
        )
    }

    dependencies {
        add("minecraft", "com.mojang:minecraft:${target.minecraft}")
        add("implementation", "net.fabricmc:fabric-loader:0.19.3")
        add("implementation", "net.fabricmc.fabric-api:fabric-api:${target.fabricApi}")
        add("implementation", "net.fabricmc:fabric-language-kotlin:1.13.12+kotlin.2.4.0")
    }

    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(25)
    }

    extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI> {
        clientOnlyMinecraftJar()
        runs {
            named("client") {
                ideConfigGenerated(true)
                runDir("run/${target.minecraft}")
            }
        }
    }

    extensions.configure<org.gradle.api.tasks.SourceSetContainer> {
        named("main") {
            java.setSrcDirs(emptyList<String>())
            resources.setSrcDirs(listOf(rootProject.file("src/main/resources")))
        }
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension> {
        sourceSets.named("main") {
            kotlin.srcDir(rootProject.file("src/main/kotlin"))
            kotlin.srcDir(rootProject.file("src/${target.minecraft}/kotlin"))
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
    }

    tasks.named<ProcessResources>("processResources") {
        inputs.property("version", artifactVersion)
        inputs.property("minecraft_version", target.minecraft)
        filesMatching("fabric.mod.json") {
            expand(
                "version" to artifactVersion,
                "minecraft_version" to target.minecraft,
            )
        }
    }

    tasks.named<Jar>("jar") {
        archiveBaseName.set("StorageLens")
        archiveVersion.set(project.version.toString())
    }
}

val releaseTargets = targets.map { (name, target) ->
    check(rootProject.file("versions/$name/build.gradle.kts").isFile) {
        "Missing versions/$name/build.gradle.kts for configured target $name"
    }

    val artifactVersion = "$modVersion+mc${target.minecraft}"
    linkedMapOf(
        "project" to name,
        "minecraft" to target.minecraft,
        "artifactVersion" to artifactVersion,
        "modrinthTask" to ":versions:$name:modrinth",
        "jar" to "versions/$name/build/libs/StorageLens-$artifactVersion.jar",
    )
}
val releaseManifestJson = JsonOutput.prettyPrint(
    JsonOutput.toJson(
        linkedMapOf(
            "modVersion" to modVersion,
            "tag" to "v$modVersion",
            "targets" to releaseTargets,
        ),
    ),
) + "\n"

tasks.register<ReleaseManifestTask>("releaseManifest") {
    group = "publishing"
    description = "Writes metadata for every supported release target."
    manifestJson.set(releaseManifestJson)
    outputFile.set(layout.buildDirectory.file("release/manifest.json"))
}

abstract class ReleaseManifestTask : DefaultTask() {
    @get:Input
    abstract val manifestJson: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeManifest() {
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(manifestJson.get())
    }
}

data class Target(
    val minecraft: String,
    val fabricApi: String,
)
