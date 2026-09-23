// VaniaMetrics: the public API, and the core plugin for Paper and Velocity.
//
// Outputs (./gradlew build):
//   api/build/libs/vania-metrics-api-<v>.jar           the public API, the only thing a
//                                                      collector author compiles against
//   paper/build/libs/VaniaMetrics-<v>-paper.jar        the core, as a Bukkit plugin
//   velocity/build/libs/VaniaMetrics-<v>-velocity.jar  the core, as a Velocity plugin
//
// No runtime dependencies. The registry, the exposition format and the HTTP server are written
// here on the JDK alone: nothing to relocate, nothing to shade. Plugin jars contain the API and
// the common core, nothing else.
//
// --release 21 rather than the JDK version: Paper servers may run Java 25, Velocity proxies
// Java 21. The lowest one wins.

// The version is read from the source, never copied: see api/.../Version.java.
val versionSource = file("api/src/main/java/fr/samflix/vaniametrics/api/Version.java")
val vaniaVersion = Regex("""VALUE = "([^"]+)"""").find(versionSource.readText())?.groupValues?.get(1)
    ?: error("cannot read the version from $versionSource")

subprojects {
    group = "fr.samflix"
    version = vaniaVersion

    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all,-path,-processing,-options", "-Werror"))
    }
}
