plugins {
    `java-platform`
    `maven-publish`
}

dependencies {
    constraints {
        api(project(":madre-algebra"))
        api(project(":madre-sdk"))
        api(project(":madre-sdk-experimental"))
        api(project(":madre-sdk-testkit"))
        api(project(":madre-reasoning-spi"))
        api(project(":madre-text-inference"))
        api(project(":madre-text-generation"))
        api(project(":madre-embeddings"))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["javaPlatform"])
            pom {
                name = "MADRE Public Artifact BOM"
                description = "Version alignment for MADRE public SDK, testkit, experimental authoring, reasoning SPI and computation contracts."
                url = "https://github.com/DidacLL/MADRE"
                licenses { license { name = "GNU Affero General Public License v3.0"; url = "https://www.gnu.org/licenses/agpl-3.0.html" } }
                developers { developer { id = "DidacLL" } }
                scm { url = "https://github.com/DidacLL/MADRE" }
            }
        }
    }
    repositories { maven { name = "isolated"; url = uri(rootProject.layout.buildDirectory.dir("isolated-repository")) } }
}
