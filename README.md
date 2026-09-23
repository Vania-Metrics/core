# vania-metrics — core

L'API publique et le noyau (Paper + Velocity) de VaniaMetrics. Build Gradle, Java 21.

```sh
./gradlew build                  # tout
./gradlew compileJava            # compile seulement
./gradlew publishToMavenLocal    # publie l'API dans ~/.m2 (avec son .pom)
```

## Organisation

| Dossier     | Projet Gradle              | Rôle                                                      |
|-------------|----------------------------|-----------------------------------------------------------|
| `api/`      | `vania-metrics-api`        | l'interface publique, sur le JDK seul — aucune dépendance |
| `common/`   | `vania-metrics-common`     | exportateur, serveur HTTP, collecteurs JVM/disque/cgroup  |
| `paper/`    | `vania-metrics-paper`      | le noyau, plugin Bukkit                                   |
| `velocity/` | `vania-metrics-velocity`   | le noyau, plugin Velocity                                 |

## Ce qui sort

```
api/build/libs/vania-metrics-api-<v>.jar
paper/build/libs/VaniaMetrics-<v>-paper.jar         API + common embarqués
velocity/build/libs/VaniaMetrics-<v>-velocity.jar   API + common embarqués
```

La version est lue dans `api/…/Version.java`, jamais recopiée. Une version publiée
se tague `vX.Y.Z` : c'est la ref que les collecteurs épinglent.

## Dépendances

Versions dans `gradle/libs.versions.toml`. Toutes les empreintes SHA-256 sont
vérifiées par Gradle (`gradle/verification-metadata.xml`). Après une montée de version :

```sh
./gradlew --write-verification-metadata sha256 build
```

## Les collecteurs

Chaque collecteur vit dans son propre dépôt (`Vania-Metrics/colecteur-<nom>`) et
inclut ce dépôt-ci comme build composite, cloné à un tag : il dépend de
`fr.samflix:vania-metrics-api`, que Gradle relie au projet `api/`.
