plugins {
    java
}

dependencies {
    implementation(project(":vania-metrics-common"))
    compileOnly(libs.paper.api)
}

tasks.processResources {
    val v = version.toString()
    inputs.property("version", v)
    filesMatching("plugin.yml") { filter { it.replace("\${version}", v) } }
}

// Un plugin est un jar autonome : l'API et le noyau commun y sont DÉPLIÉS. C'est
// tout le classpath d'exécution, puisque paper-api n'est que compileOnly.
tasks.jar {
    archiveFileName = "VaniaMetrics-$version-paper.jar"
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { cp -> cp.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
