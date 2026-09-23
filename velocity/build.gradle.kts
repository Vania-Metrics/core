plugins {
    java
}

dependencies {
    implementation(project(":vania-metrics-common"))
    compileOnly(libs.velocity.api)
    // @Plugin GÉNÈRE velocity-plugin.json : le processeur est dans velocity-api.
    annotationProcessor(libs.velocity.api)
}

// Un plugin est un jar autonome : l'API et le noyau commun y sont DÉPLIÉS.
tasks.jar {
    archiveFileName = "VaniaMetrics-$version-velocity.jar"
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { cp -> cp.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
