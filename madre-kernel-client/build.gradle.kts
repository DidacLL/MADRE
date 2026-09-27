plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val testRuntimeClasspath by configurations

val clientCliJar by tasks.registering(Jar::class) {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("cli")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(sourceSets.test.get().output)
    from({
        testRuntimeClasspath.map { file ->
            if (file.isDirectory) file else zipTree(file)
        }
    })
    manifest {
        attributes["Main-Class"] = "io.github.didacll.madre.kernel.client.KernelClientProcess"
    }
}

tasks.named("build") {
    dependsOn(clientCliJar)
}
