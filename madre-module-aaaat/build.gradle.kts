plugins {
    `java-library`
}

dependencies {
    compileOnly(project(":madre-sdk"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    testImplementation(project(":madre-sdk"))
    testImplementation(project(":madre-kernel"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("madre-module-aaaat")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) })
}
