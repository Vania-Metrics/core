// The integration-test harness: starts real servers in containers, installs the plugin jars and
// checks what they publish. Used by this build's own platform tests (src/integrationTest) and, through
// the composite build, by every collector (collector-it.gradle.kts). Never shipped in a plugin jar.
//
//   ./gradlew :vania-metrics-testkit:integrationTest            platforms marked "tested"
//   ./gradlew :vania-metrics-testkit:integrationTestUntested    the others; failures reported, not fatal
//   -Pvania.it.platforms=paper,spigot                           only these
//
// Needs a Docker API: Docker, or Podman with its socket (DOCKER_HOST).
import java.time.Duration

plugins {
    `java-library`
}

dependencies {
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter.api)
    api(libs.testcontainers)
    implementation(libs.snakeyaml)
    runtimeOnly(libs.junit.jupiter.engine)
    runtimeOnly(libs.junit.platform.launcher)
    runtimeOnly(libs.slf4j.simple)
}

// The core plugin jars under test, as this build produces them: the fat jars alone. Resolved
// without their transitive dependencies, which would drop common and api next to them.
val coreJars = configurations.dependencyScope("coreJars")
val coreJarFiles = configurations.resolvable("coreJarFiles") {
    extendsFrom(coreJars.get())
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}
dependencies {
    for (module in listOf("bukkit", "sponge", "velocity", "bungee", "geyser")) {
        add(coreJars.name, project(":vania-metrics-$module"))
    }
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)
        }
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(project())
            }
        }
    }
}

/** Same classes, two tasks: cells that must pass, and cells that are only reported. */
fun Test.platformCells(status: String) {
    val suite = sourceSets["integrationTest"]
    testClassesDirs = suite.output.classesDirs
    classpath = suite.runtimeClasspath
    useJUnitPlatform()
    val jars = files(coreJarFiles)
    inputs.files(jars).withPropertyName("coreJars")
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Dvania.it.jars=" + jars.files.joinToString(File.pathSeparator))
    }
    systemProperty("vania.it.projectDir", rootDir.path)
    systemProperty("vania.it.status", status)
    systemProperty("vania.it.platforms", providers.gradleProperty("vania.it.platforms").getOrElse(""))
    systemProperty("vania.it.reports", layout.buildDirectory.dir("vania-it").get().asFile.path)
    // Real servers, not cached results: rerun every time.
    outputs.upToDateWhen { false }
    timeout = Duration.ofMinutes(55)
    // Servers are started one after another: a CI runner has two cores.
    maxParallelForks = 1
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.named<Test>("integrationTest") {
    platformCells("tested")
}

tasks.register<Test>("integrationTestUntested") {
    description = "Runs the platform cells not marked tested; failures are reported, not fatal."
    group = "verification"
    platformCells("untested")
    ignoreFailures = !providers.gradleProperty("vania.it.strict").isPresent
    shouldRunAfter("integrationTest")
}
