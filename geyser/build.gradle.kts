plugins {
    java
}

dependencies {
    implementation(project(":vania-metrics-common"))
    compileOnly(libs.geyser.api)
    compileOnly(libs.geyser.base.api) {
        exclude(group = "org.geysermc.event", module = "events")
    }
    compileOnly(libs.geyser.events)
    compileOnly(libs.jetbrains.annotations)
}

tasks.processResources {
    val v = version.toString()
    inputs.property("version", v)
    filesMatching("extension.yml") { filter { it.replace("\${version}", v) } }
}

// An extension is a self-contained jar: the API and the common core are unpacked into it.
tasks.jar {
    archiveFileName = "vania-metrics-geyser-$version.jar"
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { cp -> cp.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
