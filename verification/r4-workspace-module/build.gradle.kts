plugins { java }

val madreRepository = providers.gradleProperty("madreRepository")
val artifactBehavior = providers.gradleProperty("artifactBehavior").orElse("v1").map { value ->
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
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.processResources {
    inputs.property("artifactBehavior", artifactBehavior)
    filesMatching("workspace-build.properties") {
        expand("artifactBehavior" to artifactBehavior.get())
    }
}

val allowedMadreArtifacts = setOf("madre-algebra", "madre-bom", "madre-sdk")

tasks.register("verifyMadreDependencyBoundary") {
    group = "verification"
    description = "Rejects MADRE product/runtime implementation dependencies from the R4 proving Module."
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
                    prefix = "R4 Module resolved forbidden MADRE implementation artifacts:\n"))
        }
    }
}

tasks.named("check") { dependsOn("verifyMadreDependencyBoundary") }

tasks.jar {
    archiveFileName.set("r4-workspace-module.jar")
    dependsOn(tasks.named("check"))
    doLast {
        val descriptorPath = "META-INF/services/io.github.didacll.madre.sdk.registration.ModuleProvider"
        val descriptors = zipTree(archiveFile.get().asFile).matching { include(descriptorPath) }.files
        if (descriptors.size != 1) {
            throw GradleException("Module JAR must contain exactly one $descriptorPath")
        }
        val provider = descriptors.single().readText().trim()
        if (provider != "r4.workspace.WorkspaceModuleProvider") {
            throw GradleException("Unexpected ModuleProvider descriptor: $provider")
        }
    }
}
