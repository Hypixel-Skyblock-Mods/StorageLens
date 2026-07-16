import java.util.Properties

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "StorageLens"

val targetProperties = Properties().apply {
    file("gradle/targets.properties").inputStream().use(::load)
}
val targetNames = targetProperties.getProperty("targets")
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.takeIf(List<String>::isNotEmpty)
    ?: error("gradle/targets.properties must declare at least one target")

targetNames.forEach { include("versions:$it") }
