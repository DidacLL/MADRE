plugins { java }

repositories { maven { url = uri("../../build/isolated-repository") } }

dependencies {
    implementation("io.github.didacll:madre-reasoning-spi:0.1.0-SNAPSHOT")
    implementation("io.github.didacll:madre-text-inference:0.1.0-SNAPSHOT")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.jar { archiveFileName.set("independent-reasoning.jar") }
