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

val writeAcceptanceClasspath by tasks.registering {
    dependsOn(tasks.testClasses)
    val output = layout.buildDirectory.file("acceptance-classpath.txt")
    outputs.file(output)
    doLast {
        output.get().asFile.writeText(sourceSets.test.get().runtimeClasspath.asPath)
    }
}

tasks.named("build") {
    dependsOn(writeAcceptanceClasspath)
}
