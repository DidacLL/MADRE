plugins { java }

dependencies {
    implementation(files("../../madre-sdk/build/libs/madre-sdk-0.1.0-SNAPSHOT.jar"))
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
