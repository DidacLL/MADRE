import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService

plugins { application }

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val shippedModules by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val shippedReasoning by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    implementation(project(":madre-kernel"))
    runtimeOnly(project(":madre-text-inference"))
    shippedModules(project(":madre-module-owner-interaction"))
    shippedReasoning(project(":madre-adapter-llamacpp"))
    shippedReasoning(project(":madre-adapter-openai-compatible"))
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass = "io.github.didacll.madre.app.MadreMain"
    applicationName = "madre"
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to "MADRE",
            "Implementation-Version" to project.version.toString(),
            "Implementation-Vendor" to "DidacLL"
        )
    }
}

distributions {
    main {
        contents {
            from(shippedModules) { into("modules") }
            from(shippedReasoning) { into("reasoning") }
            from(rootProject.file("config/madre.properties.example")) {
                into("config")
                rename { "madre.properties" }
            }
            from(rootProject.file("README.md"))
            from(rootProject.file("LICENSE"))
        }
    }
}

val windowsHost = System.getProperty("os.name").lowercase().contains("win")
val nativePackageType = if (windowsHost) "msi" else "deb"
val nativeAppVersion = project.version.toString().substringBefore('-')
val jpackageInput = layout.buildDirectory.dir("jpackage/input")
val jpackageAppImageOutput = layout.buildDirectory.dir("jpackage/app-image")
val jpackageNativeOutput = layout.buildDirectory.dir("jpackage/native")
val runtimeClasspath = configurations.named("runtimeClasspath")
val javaToolchains = extensions.getByType<JavaToolchainService>()
val jpackageExecutable = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
}.map { launcher ->
    launcher.metadata.installationPath.file("bin/${if (windowsHost) "jpackage.exe" else "jpackage"}").asFile
}

val stageJpackageInput by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Stages the application runtime and shipped artifacts for jpackage."
    dependsOn(tasks.named("jar"))
    into(jpackageInput)
    from(tasks.named("jar"))
    from(runtimeClasspath)
    from(shippedModules) { into("modules") }
    from(shippedReasoning) { into("reasoning") }
    from(rootProject.file("config/madre.bootstrap.properties")) {
        into("defaults")
        rename { "madre.properties" }
    }
    from(rootProject.file("README.md"))
    from(rootProject.file("LICENSE"))
}

val jpackageAppImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds an isolated MADRE application image with a bundled Java runtime."
    dependsOn(stageJpackageInput)
    outputs.dir(jpackageAppImageOutput)
    doFirst {
        project.delete(jpackageAppImageOutput.get().asFile)
        val mainJar = tasks.named<Jar>("jar").get().archiveFile.get().asFile.name
        val command = mutableListOf(
            jpackageExecutable.get().absolutePath,
            "--type", "app-image",
            "--dest", jpackageAppImageOutput.get().asFile.absolutePath,
            "--name", "madre",
            "--input", jpackageInput.get().asFile.absolutePath,
            "--main-jar", mainJar,
            "--main-class", "io.github.didacll.madre.app.MadreMain",
            "--app-version", nativeAppVersion,
            "--vendor", "DidacLL",
            "--description", "MADRE local-first modular agentic system"
        )
        if (windowsHost) command += "--win-console"
        commandLine(*command.toTypedArray())
    }
}

val nativePackage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds the host-native MADRE installer with a bundled Java runtime."
    dependsOn(stageJpackageInput)
    outputs.dir(jpackageNativeOutput)
    doFirst {
        project.delete(jpackageNativeOutput.get().asFile)
        jpackageNativeOutput.get().asFile.mkdirs()
        val mainJar = tasks.named<Jar>("jar").get().archiveFile.get().asFile.name
        val command = mutableListOf(
            jpackageExecutable.get().absolutePath,
            "--type", nativePackageType,
            "--dest", jpackageNativeOutput.get().asFile.absolutePath,
            "--name", "madre",
            "--input", jpackageInput.get().asFile.absolutePath,
            "--main-jar", mainJar,
            "--main-class", "io.github.didacll.madre.app.MadreMain",
            "--app-version", nativeAppVersion,
            "--vendor", "DidacLL",
            "--description", "MADRE local-first modular agentic system"
        )
        if (windowsHost) {
            command += "--win-console"
            command += "--win-per-user-install"
        } else {
            command += listOf("--linux-package-name", "madre")
        }
        commandLine(*command.toTypedArray())
    }
}

tasks.test { useJUnitPlatform() }
