plugins { `java-library` }

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror")) }

dependencies {
    api(project(":madre-reasoning-spi"))
    api(project(":madre-text-inference"))
    api(project(":madre-text-generation"))
    api(project(":madre-embeddings"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
tasks.test { useJUnitPlatform() }
