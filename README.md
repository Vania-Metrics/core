<img src="icon.png" alt="" width="96" align="right">

# VaniaMetrics core

The public API and the core plugin of VaniaMetrics, a Prometheus exporter for Minecraft servers.
Gradle build, Java 21.

| Loader | Jar | Tested on |
|---|---|---|
| CraftBukkit, Spigot, Paper, Purpur, Folia | `vania-metrics-bukkit` | Spigot, Paper, Purpur, Folia 1.21.11 |
| Sponge (API 17) | `vania-metrics-sponge` | SpongeVanilla 1.21.10 |
| Velocity | `vania-metrics-velocity` | Velocity 3.5 / 4.2 |
| BungeeCord, Waterfall | `vania-metrics-bungee` | BungeeCord, Waterfall build 615 |
| Geyser (extension) | `vania-metrics-geyser` | Geyser Standalone 2.11.3 |

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
| `velocity/` | `vania-metrics-velocity` | the proxy core, as a Velocity plugin                   |
| `bungee/`   | `vania-metrics-bungee`   | the proxy core, as a BungeeCord/Waterfall plugin       |
| `geyser/`   | `vania-metrics-geyser`   | the proxy core, as a Geyser extension, plus Bedrock device/input metrics |

## Outputs

```
api/build/libs/vania-metrics-api-<v>.jar
bukkit/build/libs/vania-metrics-bukkit-<v>.jar       API + common bundled
sponge/build/libs/vania-metrics-sponge-<v>.jar       API + common bundled
velocity/build/libs/vania-metrics-velocity-<v>.jar   API + common bundled
```

The version is read from `api/.../Version.java`, never copied. Releases are made by
release-please: commit messages start with a type (`feat:`, `fix:`, `chore:`…), every push to
`main` updates a release pull request, and merging it tags `vX.Y.Z` — the ref collectors pin — and
publishes the GitHub release with the jar of every platform.

## Tests

```sh
./gradlew build                                        # unit tests, no server
./gradlew :vania-metrics-testkit:integrationTest       # real servers, the platforms marked tested
./gradlew :vania-metrics-testkit:integrationTestUntested   # the others: reported, not fatal
./gradlew :vania-metrics-testkit:integrationTest -Pvania.it.platforms=paper,purpur
```

- **Unit tests** (`api`, `common`) run against fake servers. `common/src/test/resources/contract/`
  pins every published family with its type and labels, per capability profile: a rename fails
  the build. A deliberate change is recorded with `-Pvania.contract.update`, and the diff is what
  gets reviewed.
- **Integration tests** (`testkit`) start each platform of `compatibility.yml` in a container
  (itzg images, pinned by digest), install the jar, let a bot join and leave, and check what
  `/metrics` publishes, then that the server stops cleanly and its log holds no error of ours.
  They need a Docker API: Docker, or Podman with its user socket (`DOCKER_HOST`). Results and logs
  go to `testkit/build/vania-it/`.
- **Collectors** run the same harness: `testkit/collector-it.gradle.kts` wires it into their
  build, `collector-test.yml` says what to install besides the core.
- **CI**: `build.yml` on every push (unit tests and Paper), `platforms.yml` monthly and by hand,
  `collector.yml` called by every collector repository. Each run on `main` ends by recording its
  results for the [documentation site](https://vania-metrics.github.io/compatibility).

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

## License

[GNU General Public License v3.0](LICENSE).
