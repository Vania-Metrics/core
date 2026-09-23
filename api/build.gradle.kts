// L'API se compile SEULE, et c'est la garantie qui compte : si elle compile sans
// paper-api ni velocity-api, c'est qu'elle n'en dépend pas, et qu'un collecteur
// tiers peut s'y lier sans rien traîner. Aucune dépendance ici, et ça doit durer.
plugins {
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("api") {
            from(components["java"])
            pom {
                name = "VaniaMetrics API"
                description = "Interface publique de l'exportateur Prometheus VaniaMetrics."
            }
        }
    }
}
