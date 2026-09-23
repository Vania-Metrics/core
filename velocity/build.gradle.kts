plugins {
    java
}

dependencies {
    implementation(project(":vania-metrics-common"))
    compileOnly(libs.velocity.api)
    // Generates velocity-plugin.json from @Plugin.
    annotationProcessor(libs.velocity.api)
}

// A plugin is a self-contained jar: the API and the common core are unpacked into it.
tasks.jar {
    archiveFileName = "vania-metrics-velocity-$version.jar"
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { cp -> cp.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
