plugins {
    java
}

dependencies {
    implementation(project(":vania-metrics-common"))
    compileOnly(libs.bundles.paper)
}

tasks.processResources {
    val v = version.toString()
    inputs.property("version", v)
    filesMatching("plugin.yml") { filter { it.replace("\${version}", v) } }
}

// A plugin is a self-contained jar: the API and the common core are unpacked into it. That is the
// whole runtime classpath, since paper-api is compileOnly.
tasks.jar {
    archiveFileName = "vania-metrics-paper-$version.jar"
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { cp -> cp.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
