plugins { java }

val madreRepository = providers.gradleProperty("madreRepository")
        .orElse("../../build/isolated-repository")

repositories {
    maven { url = uri(madreRepository.get()) }
    mavenCentral()
}

dependencies {
    implementation(platform("io.github.didacll:madre-bom:0.1.0-SNAPSHOT"))
    implementation("io.github.didacll:madre-sdk")
    implementation("io.github.didacll:madre-text-inference")

    testImplementation(platform("io.github.didacll:madre-bom:0.1.0-SNAPSHOT"))
    testImplementation("io.github.didacll:madre-sdk-testkit")
    testImplementation("io.github.didacll:madre-sdk-experimental")
    testImplementation("io.github.didacll:madre-text-generation")
    testImplementation("io.github.didacll:madre-embeddings")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test { useJUnitPlatform() }

tasks.jar {
    archiveFileName.set("independent-module.jar")
    dependsOn(tasks.test)
    doLast {
        val descriptorPath = "META-INF/services/io.github.didacll.madre.sdk.registration.ModuleProvider"
        val descriptors = zipTree(archiveFile.get().asFile).matching {
            include(descriptorPath)
        }.files
        if (descriptors.size != 1) {
            throw GradleException("Module JAR must contain exactly one $descriptorPath")
        }
        val provider = descriptors.single().readText().trim()
        if (provider != "consumer.IndependentModuleProvider") {
            throw GradleException("Unexpected ModuleProvider descriptor: $provider")
        }
    }
}
