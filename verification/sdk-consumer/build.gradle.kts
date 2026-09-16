plugins { java }

val madreRepository = providers.gradleProperty("madreRepository")
val artifactBehavior = providers.gradleProperty("artifactBehavior").orElse("baseline").map { value ->
    val normalized = value.trim()
    if (!Regex("[A-Za-z0-9._-]+").matches(normalized)) {
        throw GradleException("artifactBehavior must match [A-Za-z0-9._-]+")
    }
    normalized
}

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

tasks.processResources {
    inputs.property("artifactBehavior", artifactBehavior)
    filesMatching("consumer-build.properties") {
        expand("artifactBehavior" to artifactBehavior.get())
    }
}

tasks.test { useJUnitPlatform() }

val allowedMadreArtifacts = setOf(
    "madre-algebra",
    "madre-bom",
    "madre-sdk",
    "madre-sdk-testkit",
    "madre-text-inference",
    "madre-text-generation",
    "madre-embeddings"
)

tasks.register("verifyMadreDependencyBoundary") {
    group = "verification"
    description = "Rejects MADRE runtime/product implementation dependencies from the independent Module project."
    doLast {
        val violations = mutableSetOf<String>()
        listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")
                .forEach { configurationName ->
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
                    prefix = "Independent Module resolved forbidden MADRE implementation artifacts:\n"))
        }
    }
}

tasks.named("check") {
    dependsOn("verifyMadreDependencyBoundary")
}

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
