// Integration tests for a collector, on real servers. Applied from a collector's build.gradle.kts,
// after the java plugin:
//
//   apply(from = vaniaCoreDir.resolve("testkit/collector-it.gradle.kts"))
//
// Shipped with the core so that the wiring moves with the testkit it drives: a collector pinned
// to a core tag gets the harness of that tag. It adds:
//
//   src/integrationTest/java      one class: class SmokeIT extends CollectorSmokeTest {}
//   collector-test.yml            what the test server needs besides the core (see
//                                 CollectorTestConfig)
//   ./gradlew integrationTest            cells compatibility.yml says must work (collector: yes)
//   ./gradlew integrationTestUntested    the others, reported but not fatal; -Pvania.it.strict
//                                        makes them fatal, -Pvania.it.all adds the "no" cells
//   -Pvania.it.platforms=paper,purpur    only these
//
// The core plugin jars come from the included core build, the collector's from this one.
import java.time.Duration

val sourceSets = extensions.getByType<SourceSetContainer>()
val integrationTestSources = sourceSets.create("integrationTest")
dependencies.add(integrationTestSources.implementationConfigurationName, "fr.samflix:vania-metrics-testkit")

// The core jars a cell may need. The Bukkit one always; a proxy family's only when the manifest
// claims one of its platforms (or when -Pvania.it.all asks for the "no" cells too): each one is
// a core module to build and its dependencies to verify.
val manifest = file("compatibility.yml").readText()
val all = providers.gradleProperty("vania.it.all").isPresent
fun claims(platform: String): Boolean {
    val line = Regex("""(?m)^\s*$platform:\s*\{([^}]*)\}""").find(manifest)?.groupValues?.get(1) ?: return false
    return Regex("""collector:\s*(yes|untested)""").containsMatchIn(line) ||
        (all && Regex("""plugin:\s*(yes|bundled)""").containsMatchIn(line))
}
val vaniaCoreJars = configurations.dependencyScope("vaniaCoreJars")
val vaniaCoreJarFiles = configurations.resolvable("vaniaCoreJarFiles") {
    extendsFrom(vaniaCoreJars.get())
    // The fat jar alone: its transitive dependencies (common, api) are already inside it.
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}
dependencies.add(vaniaCoreJars.name, "fr.samflix:vania-metrics-bukkit")
if (claims("velocity")) {
    dependencies.add(vaniaCoreJars.name, "fr.samflix:vania-metrics-velocity")
}
if (claims("bungeecord") || claims("waterfall")) {
    dependencies.add(vaniaCoreJars.name, "fr.samflix:vania-metrics-bungee")
}

val collectorJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
val pluginJars = files(vaniaCoreJarFiles, collectorJar)

fun Test.cells(status: String) {
    testClassesDirs = integrationTestSources.output.classesDirs
    classpath = integrationTestSources.runtimeClasspath
    useJUnitPlatform()
    inputs.files(pluginJars).withPropertyName("pluginJars")
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Dvania.it.jars=" + pluginJars.files.joinToString(File.pathSeparator))
    }
    systemProperty("vania.it.projectDir", projectDir.path)
    systemProperty("vania.it.status", status)
    systemProperty("vania.it.platforms", providers.gradleProperty("vania.it.platforms").getOrElse(""))
    systemProperty("vania.it.all", all)
    systemProperty("vania.it.reports", layout.buildDirectory.dir("vania-it").get().asFile.path)
    // Real servers, not cached results: rerun every time.
    outputs.upToDateWhen { false }
    timeout = Duration.ofMinutes(60)
    // One server at a time: a CI runner has two cores.
    maxParallelForks = 1
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs the collector on the platforms its manifest says it works on."
    group = "verification"
    cells("tested")
}

tasks.register<Test>("integrationTestUntested") {
    description = "Runs the collector on the platforms its manifest has not vouched for; failures are reported, not fatal."
    group = "verification"
    cells("untested")
    ignoreFailures = !providers.gradleProperty("vania.it.strict").isPresent
    shouldRunAfter(integrationTest)
}
