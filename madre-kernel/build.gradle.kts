plugins { `java-library` }

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

tasks.test { useJUnitPlatform() }
