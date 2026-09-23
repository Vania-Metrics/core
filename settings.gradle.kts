rootProject.name = "vania-metrics"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
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
