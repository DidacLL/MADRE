plugins {
    `java-library`
    application
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

dependencies {
    api(project(":madre-sdk"))
    implementation(project(":madre-kernel"))
    implementation(project(":madre-inference-llamacpp"))
    implementation(project(":madre-inference-openai-compatible"))
    runtimeOnly(project(":madre-module-owner-interaction"))
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass = "io.github.didacll.madre.runtime.MadreMain"
    applicationName = "madre"
}

tasks.test { useJUnitPlatform() }
