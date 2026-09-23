// Le noyau commun aux deux plateformes : exportateur, serveur HTTP, collecteurs
// JVM, disque et cgroup. Il ne connaît ni Paper ni Velocity — le JDK et l'API.
// Il embarque aussi metrics.properties, la configuration par défaut.
plugins {
    `java-library`
}

dependencies {
    api(project(":vania-metrics-api"))
}
