plugins {
    base
}

group = "io.github.didacll"
version = "0.1.0-SNAPSHOT"

tasks.named("build") {
    dependsOn(":madre-kernel-client:build")
}
