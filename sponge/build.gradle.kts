plugins {
    java
}

dependencies {
    implementation(project(":vania-metrics-common"))
    compileOnly(libs.sponge.api)
    // Adventure, pulled in by SpongeAPI, is annotated with these; without them javac warns.
    compileOnly(libs.jetbrains.annotations)
}

tasks.processResources {
    val v = version.toString()
    inputs.property("version", v)
    filesMatching("META-INF/sponge_plugins.json") { filter { it.replace("\${version}", v) } }
}

// A plugin is a self-contained jar: the API and the common core are unpacked into it.
tasks.jar {
    archiveFileName = "vania-metrics-sponge-$version.jar"
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { cp -> cp.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
