plugins {
    base
}

allprojects {
    group = "io.github.didacll"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

tasks.register("architectureCheck") {
    group = "verification"
    description = "Rejects production-source structures forbidden by the active MADRE architecture."
    doLast {
        val production = fileTree(rootDir) {
            include("madre-*/src/main/**/*.java")
        }
        val forbidden = mapOf(
            "generic public metadata bags" to Regex("Map<String,\\s*Object>"),
            "autonomous surfaces" to Regex("(Input|Output|Responsibility|Security)Surface"),
            "security decision subsystems" to Regex("Security(Decision|Evidence|History|Evaluator|Checker)"),
            "assistant or universal turn abstractions" to Regex("(Assistant|AgentLoop|AssistantTurn|ModelToolCall)"),
            "credential models" to Regex("(Credential|Authorization|Clearance|ProviderTrust)"),
            "removed generic Kernel execution service" to Regex("\\bExecutionService\\b"),
            "removed generic Kernel work request" to Regex("\\bWorkRequest\\b"),
            "removed generic Kernel Capability package" to Regex("io\\.github\\.didacll\\.madre\\.kernel\\.capability")
        )
        val violations = mutableListOf<String>()
        production.forEach { source ->
            val text = source.readText()
            forbidden.forEach { (reason, pattern) ->
                if (pattern.containsMatchIn(text)) {
                    violations += "${source.relativeTo(rootDir)}: $reason"
                }
            }
        }
        val python = fileTree(rootDir) {
            include("**/*.py")
            exclude(".git/**", "build/**", "**/build/**")
        }
        python.forEach { violations += "${it.relativeTo(rootDir)}: active Python source" }

        fileTree(rootDir) {
            include("madre-kernel/src/main/java/**/reasoning/*.java")
        }.forEach { source ->
            val text = source.readText()
            Regex("\\b(Material(Type)?|Module|Agent|Operation|Skill|Workflow)(Id)?\\b")
                    .find(text)?.let {
                        violations += "${source.relativeTo(rootDir)}: semantic concept inside ReasoningCapability SPI"
                    }
        }
        fileTree(rootDir) { include("madre-kernel/src/main/**/*.java") }.forEach { source ->
            if (Regex("new\\s+Material\\s*[<(]").containsMatchIn(source.readText())) {
                violations += "${source.relativeTo(rootDir)}: Kernel-created Material"
            }
        }

        val searxngBuild = file("madre-adapter-searxng/build.gradle.kts")
        if (searxngBuild.exists() && searxngBuild.readText().contains("madre-kernel")) {
            violations += "madre-adapter-searxng/build.gradle.kts: search adapter depends on Kernel"
        }
        fileTree(rootDir) {
            include("madre-adapter-searxng/src/main/**/*.java")
        }.forEach { source ->
            if (source.readText().contains("ReasoningCapability")) {
                violations += "${source.relativeTo(rootDir)}: search adapter entered reasoning registry boundary"
            }
        }
        if (file("settings.gradle.kts").readText().contains("madre-module-web-search")) {
            violations += "settings.gradle.kts: standalone WebSearch Module restored"
        }

        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString("\n", "Architecture violations:\n"))
        }
    }
}

tasks.named("check") {
    dependsOn("architectureCheck")
    dependsOn(subprojects.map { "${it.path}:check" })
}
