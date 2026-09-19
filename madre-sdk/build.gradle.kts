plugins {
    `java-library`
    `maven-publish`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = "MADRE Module SDK"
                description = "Public strongly typed Module construction model for MADRE."
                url = "https://github.com/DidacLL/MADRE"
                licenses { license { name = "GNU Affero General Public License v3.0"; url = "https://www.gnu.org/licenses/agpl-3.0.html" } }
                developers { developer { id = "DidacLL" } }
                scm { url = "https://github.com/DidacLL/MADRE" }
            }
        }
    }
    repositories { maven { name = "isolated"; url = uri(rootProject.layout.buildDirectory.dir("isolated-repository")) } }
}
