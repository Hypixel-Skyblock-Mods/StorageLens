plugins { `java-library` }
group = "org.notenoughupdates.moulconfig"
version = "4.7.2+mc26.3.4eaeb73cdaeb"
val dependencyJar = (rootProject.extra["moulConfig263"] as FileCollection).singleFile
tasks.named("jar") { enabled = false }
listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.artifacts.clear()
        outgoing.artifact(dependencyJar) { builtBy(rootProject.tasks.named("buildMoulConfig263")) }
    }
}
