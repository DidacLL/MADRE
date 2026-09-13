plugins { application }

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

dependencies {
    implementation(project(":madre-module-owner-interaction"))
    implementation(project(":madre-module-web-search"))
    implementation(project(":madre-kernel"))
    implementation(project(":madre-adapter-llamacpp"))
    implementation(project(":madre-adapter-openai-compatible"))
    implementation(project(":madre-adapter-searxng"))
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass = "io.github.didacll.madre.app.MadreMain"
    applicationName = "madre"
}

distributions {
    main {
        contents {
            from(rootProject.file("config/madre.properties.example")) {
                into("config")
                rename { "madre.properties" }
            }
            from(rootProject.file("README.md"))
            from(rootProject.file("LICENSE"))
        }
    }
}

tasks.test { useJUnitPlatform() }
