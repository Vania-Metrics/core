rootProject.name = "vania-metrics"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // PAPER NE PUBLIE QUE DES INSTANTANÉS DE SON API, et on en épingle un par
        // son horodatage. Lu par le dépôt Maven, Gradle le traite en SNAPSHOT et sa
        // vérification des empreintes plante en écrivant verification-metadata.xml
        // — voire écrit un fichier incomplet (gradle/gradle#32739, #26803, ouverts).
        // Lu par ce dépôt ivy, c'est une version comme une autre, et le même fichier.
        // Le prix : aucune dépendance transitive (le .module de Paper désigne le jar
        // sous son nom -SNAPSHOT, introuvable ici). Elles sont donc déclarées à la
        // main, dans le bundle « paper » du catalogue.
        exclusiveContent {
            forRepository {
                ivy("https://repo.papermc.io/repository/maven-public/") {
                    name = "paperInstantanesFiges"
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
    }
}

// LE NOM DU PROJET EST LA COORDONNÉE MAVEN. Un collecteur qui inclut ce dépôt
// (includeBuild) dépend de « fr.samflix:vania-metrics-api » : Gradle le relie à
// ce projet par son nom, pas par le nom du jar. D'où ces noms longs, et des
// dossiers courts.
include("vania-metrics-api", "vania-metrics-common", "vania-metrics-paper", "vania-metrics-velocity")
project(":vania-metrics-api").projectDir = file("api")
project(":vania-metrics-common").projectDir = file("common")
project(":vania-metrics-paper").projectDir = file("paper")
project(":vania-metrics-velocity").projectDir = file("velocity")
