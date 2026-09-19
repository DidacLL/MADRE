plugins {
    base
}

allprojects {
    group = "io.github.didacll"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
}
