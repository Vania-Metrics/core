// The core shared by both platforms: exporter, HTTP server, JVM/disk/cgroup collectors, and the
// default metrics.properties. It knows neither Paper nor Velocity, only the JDK and the API.
plugins {
    `java-library`
}

dependencies {
    api(project(":vania-metrics-api"))
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)
            targets.all {
                testTask.configure {
                    // The metric contract lives next to the tests; -Pvania.contract.update rewrites
                    // it instead of comparing, and the diff is reviewed at commit time.
                    systemProperty("vania.contract.dir", file("src/test/resources/contract").path)
                    systemProperty("vania.contract.update",
                            providers.gradleProperty("vania.contract.update").isPresent)
                }
            }
        }
    }
}
