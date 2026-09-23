// The core shared by both platforms: exporter, HTTP server, JVM/disk/cgroup collectors, and the
// default metrics.properties. It knows neither Paper nor Velocity, only the JDK and the API.
plugins {
    `java-library`
}

dependencies {
    api(project(":vania-metrics-api"))
}
