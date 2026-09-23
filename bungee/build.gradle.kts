plugins {
    java
}

dependencies {
    implementation(project(":vania-metrics-common"))
    compileOnly(libs.bungeecord.api)
}

tasks.processResources {
    val v = version.toString()
    inputs.property("version", v)
    filesMatching("bungee.yml") { filter { it.replace("\${version}", v) } }
}

// A plugin is a self-contained jar: the API and the common core are unpacked into it.
tasks.jar {
    archiveFileName = "vania-metrics-bungee-$version.jar"
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { cp -> cp.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
