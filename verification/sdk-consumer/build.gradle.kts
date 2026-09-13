plugins { java }

repositories { maven { url = uri("../../build/isolated-repository") } }

dependencies { implementation("io.github.didacll:madre-sdk:0.1.0-SNAPSHOT") }

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
