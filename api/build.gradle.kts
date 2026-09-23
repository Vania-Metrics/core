// The API compiles on its own, and that is the guarantee that matters: if it compiles without
// paper-api or velocity-api, it does not depend on them, and a third-party collector can link
// against it without pulling anything in. Keep this module dependency-free.
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
                description = "Public API of the VaniaMetrics Prometheus exporter."
            }
        }
    }
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)
        }
    }
}
