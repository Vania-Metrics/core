# VaniaMetrics core

The public API and the core plugin of VaniaMetrics, a Prometheus exporter for Minecraft servers.
Gradle build, Java 21.

| Loader | Jar | Tested on |
|---|---|---|
| CraftBukkit, Spigot, Paper, Purpur, Folia | `vania-metrics-bukkit` | Spigot, Paper, Purpur, Folia 1.21.11 |
| Sponge (API 17) | `vania-metrics-sponge` | SpongeVanilla 1.21.10 |
| Velocity | `vania-metrics-velocity` | Velocity 3.5 |

One Bukkit jar covers the whole family: optional APIs (Paper's tick buffer and world counters,
client brand) are probed at startup, and Folia gets region schedulers instead of the main thread.
A metric a loader cannot provide is left out, never faked.

```sh
./gradlew build                  # everything
./gradlew compileJava            # compile only
./gradlew publishToMavenLocal    # publish the API to ~/.m2 (with its .pom)
```

## Layout

| Directory   | Gradle project           | Contents                                               |
|-------------|--------------------------|--------------------------------------------------------|
| `api/`      | `vania-metrics-api`      | the public API, JDK only, no dependencies              |
| `common/`   | `vania-metrics-common`   | exporter, HTTP server, JVM/disk/cgroup collectors, and the game collectors (tick, worlds, players, events) behind a neutral `GameServer` interface |
| `bukkit/`   | `vania-metrics-bukkit`   | the core, as a Bukkit/Spigot/Paper/Purpur/Folia plugin |
| `sponge/`   | `vania-metrics-sponge`   | the core, as a Sponge plugin                           |
| `velocity/` | `vania-metrics-velocity` | the core, as a Velocity plugin                         |

## Outputs

```
api/build/libs/vania-metrics-api-<v>.jar
bukkit/build/libs/vania-metrics-bukkit-<v>.jar       API + common bundled
sponge/build/libs/vania-metrics-sponge-<v>.jar       API + common bundled
velocity/build/libs/vania-metrics-velocity-<v>.jar   API + common bundled
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

Each collector lives in its own repository (`Vania-Metrics/collector-<name>`) and includes this
repository as a composite build, checked out at a tag: it depends on
`fr.samflix:vania-metrics-api`, which Gradle substitutes with the `api/` project.
