# VaniaMetrics core

The public API and the core plugin (Paper and Velocity) of VaniaMetrics, a Prometheus exporter
for Minecraft servers. Gradle build, Java 21.

```sh
./gradlew build                  # everything
./gradlew compileJava            # compile only
./gradlew publishToMavenLocal    # publish the API to ~/.m2 (with its .pom)
```

## Layout

| Directory   | Gradle project           | Contents                                               |
|-------------|--------------------------|--------------------------------------------------------|
| `api/`      | `vania-metrics-api`      | the public API, JDK only, no dependencies              |
| `common/`   | `vania-metrics-common`   | exporter, HTTP server, JVM/disk/cgroup collectors      |
| `paper/`    | `vania-metrics-paper`    | the core, as a Bukkit plugin                           |
| `velocity/` | `vania-metrics-velocity` | the core, as a Velocity plugin                         |

## Outputs

```
api/build/libs/vania-metrics-api-<v>.jar
paper/build/libs/VaniaMetrics-<v>-paper.jar         API + common bundled
velocity/build/libs/VaniaMetrics-<v>-velocity.jar   API + common bundled
```

The version is read from `api/.../Version.java`, never copied. Releases are tagged `vX.Y.Z`;
that is the ref collectors pin.

## Dependencies

Versions live in `gradle/libs.versions.toml`. Gradle verifies every SHA-256 checksum against
`gradle/verification-metadata.xml`. After a version bump:

```sh
./gradlew --write-verification-metadata sha256 build
```

## Collectors

Each collector lives in its own repository (`Vania-Metrics/colecteur-<name>`) and includes this
repository as a composite build, checked out at a tag: it depends on
`fr.samflix:vania-metrics-api`, which Gradle substitutes with the `api/` project.
