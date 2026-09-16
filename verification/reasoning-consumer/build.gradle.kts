plugins { java }

val madreRepository = providers.gradleProperty("madreRepository")

repositories {
    maven { url = uri(madreRepository.get()) }
    mavenCentral()
}

dependencies {
    implementation(platform("io.github.didacll:madre-bom:0.1.0-SNAPSHOT"))
    implementation("io.github.didacll:madre-reasoning-spi")
    implementation("io.github.didacll:madre-text-inference")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val allowedMadreArtifacts = setOf(
    "madre-algebra",
    "madre-bom",
    "madre-sdk",
    "madre-reasoning-spi",
    "madre-text-inference"
)

tasks.register("verifyMadreDependencyBoundary") {
    group = "verification"
    description = "Rejects MADRE runtime/product implementation dependencies from the independent reasoning provider."
    doLast {
        val violations = mutableSetOf<String>()
        listOf("compileClasspath", "runtimeClasspath").forEach { configurationName ->
            configurations.getByName(configurationName).incoming.resolutionResult.allComponents
                    .forEach { component ->
                        val moduleVersion = component.moduleVersion
                        if (moduleVersion != null
                                && moduleVersion.group == "io.github.didacll"
                                && moduleVersion.name !in allowedMadreArtifacts) {
                            violations += "$configurationName -> ${moduleVersion.group}:${moduleVersion.name}:${moduleVersion.version}"
                        }
                    }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(violations.sorted().joinToString(
                    separator = "\n",
                    prefix = "Independent reasoning provider resolved forbidden MADRE implementation artifacts:\n"))
        }
    }
}

tasks.named("check") {
    dependsOn("verifyMadreDependencyBoundary")
}

tasks.jar {
    archiveFileName.set("independent-reasoning.jar")
    doLast {
        val descriptorPath =
                "META-INF/services/io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider"
        val descriptors = zipTree(archiveFile.get().asFile).matching { include(descriptorPath) }.files
        if (descriptors.size != 1) {
            throw GradleException("Reasoning JAR must contain exactly one $descriptorPath")
        }
        val provider = descriptors.single().readText().trim()
        if (provider != "fixture.IndependentTextReasoningProvider") {
            throw GradleException("Unexpected ReasoningMechanismProvider descriptor: $provider")
        }
    }
}
