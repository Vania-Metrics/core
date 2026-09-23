// =============================================================================
// VaniaMetrics — l'API, et le noyau pour Paper et pour Velocity
// =============================================================================
// CE QUI SORT (./gradlew build) :
//
//   api/build/libs/vania-metrics-api-<v>.jar         l'interface publique, la
//                                                    seule chose qu'un auteur de
//                                                    collecteur compile
//   paper/build/libs/VaniaMetrics-<v>-paper.jar      le noyau, plugin Bukkit
//   velocity/build/libs/VaniaMetrics-<v>-velocity.jar le noyau, plugin Velocity
//
// ./gradlew publishToMavenLocal publie l'API dans ~/.m2, avec son .pom.
//
// AUCUNE DÉPENDANCE À L'EXÉCUTION. Le registre, le format d'exposition et le
// serveur HTTP sont écrits ici, sur le JDK seul : rien à reloger, rien à ombrer.
// Les jars de plugin embarquent l'API et le noyau commun, et rien d'autre.
//
// --release 21 ET PAS LA VERSION DU JDK : le lobby vise Java 25, le proxy
// Java 21. Le plus petit commande.
// =============================================================================

// La version est lue dans la SOURCE, jamais recopiée : voir api/…/Version.java.
val versionSource = file("api/src/main/java/fr/samflix/vaniametrics/api/Version.java")
val vaniaVersion = Regex("""VALEUR = "([^"]+)"""").find(versionSource.readText())?.groupValues?.get(1)
    ?: error("version illisible dans $versionSource")

subprojects {
    group = "fr.samflix"
    version = vaniaVersion

    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all,-path,-processing,-options", "-Werror"))
    }
}
