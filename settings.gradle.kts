rootProject.name = "vania-metrics"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // Paper only publishes snapshots of its API, and we pin one by its timestamp. Resolved
        // through the Maven repository, Gradle treats it as a SNAPSHOT and its checksum
        // verification fails while writing verification-metadata.xml, or writes an incomplete
        // file (gradle/gradle#32739, #26803, both open). Resolved through this ivy repository,
        // it is an ordinary version, and the same file.
        // The cost: no transitive dependencies (Paper's .module points to the jar under its
        // -SNAPSHOT name, which does not exist here). They are declared by hand instead, in the
        // "paper" bundle of the version catalog.
        exclusiveContent {
            forRepository {
                ivy("https://repo.papermc.io/repository/maven-public/") {
                    name = "paperPinnedSnapshots"
                    patternLayout {
                        setM2compatible(true)
                        artifact("[organisation]/[module]/1.21.11-R0.1-SNAPSHOT/[module]-[revision].[ext]")
                    }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("io.papermc.paper", "paper-api") }
        }
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        // The Geyser API is only published as snapshots; one is pinned by timestamp and read as a
        // fixed version, for the same reason as paper-api above.
        exclusiveContent {
            forRepository {
                ivy("https://repo.opencollab.dev/main/") {
                    name = "geyserPinnedSnapshots"
                    patternLayout {
                        setM2compatible(true)
                        artifact("[organisation]/[module]/2.11.2-SNAPSHOT/[module]-[revision].[ext]")
                    }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.geysermc.geyser", "api") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://repo.opencollab.dev/main/") {
                    name = "geyserEventsPinnedSnapshot"
                    patternLayout {
                        setM2compatible(true)
                        artifact("[organisation]/[module]/1.1-SNAPSHOT/[module]-[revision].[ext]")
                    }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.geysermc.event", "events") }
        }
        maven("https://repo.opencollab.dev/main/") {
            content {
                includeGroupByRegex("org\\.geysermc.*")
                includeGroupByRegex("org\\.cloudburstmc.*")
            }
        }
        maven("https://repo.spongepowered.org/repository/maven-public/") {
            content { includeGroup("org.spongepowered") }
        }
    }
}

// The project name is the Maven coordinate. A collector that includes this build (includeBuild)
// depends on "fr.samflix:vania-metrics-api", and Gradle substitutes this project by name, not by
// jar name. Hence the long project names and short directories.
include("vania-metrics-api", "vania-metrics-common", "vania-metrics-bukkit", "vania-metrics-velocity", "vania-metrics-sponge", "vania-metrics-bungee", "vania-metrics-geyser", "vania-metrics-testkit")
project(":vania-metrics-api").projectDir = file("api")
project(":vania-metrics-common").projectDir = file("common")
project(":vania-metrics-bukkit").projectDir = file("bukkit")
project(":vania-metrics-velocity").projectDir = file("velocity")
project(":vania-metrics-sponge").projectDir = file("sponge")
project(":vania-metrics-bungee").projectDir = file("bungee")
project(":vania-metrics-geyser").projectDir = file("geyser")
project(":vania-metrics-testkit").projectDir = file("testkit")
