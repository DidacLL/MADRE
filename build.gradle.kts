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
            include("madre-reasoning-spi/src/main/java/**/reasoning/*.java")
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

        val reasoningSpiBuild = file("madre-reasoning-spi/build.gradle.kts")
        if (reasoningSpiBuild.exists() && reasoningSpiBuild.readText().contains("madre-kernel")) {
            violations += "madre-reasoning-spi/build.gradle.kts: public reasoning SPI depends on Kernel runtime"
        }
        listOf("madre-adapter-llamacpp", "madre-adapter-openai-compatible").forEach { projectName ->
            val adapterBuild = file("$projectName/build.gradle.kts")
            if (adapterBuild.exists() && adapterBuild.readText().contains("madre-kernel")) {
                violations += "$projectName/build.gradle.kts: reasoning adapter depends on Kernel runtime"
            }
        }
        val appBuild = file("madre-app/build.gradle.kts")
        if (Regex("implementation\\(project\\(\":madre-adapter-(llamacpp|openai-compatible)\"\\)\\)")
                .containsMatchIn(appBuild.readText())) {
            violations += "madre-app/build.gradle.kts: application has a concrete reasoning-adapter implementation dependency"
        }
        if (Regex("implementation\\(project\\(\":madre-module-owner-interaction\"\\)\\)")
                .containsMatchIn(appBuild.readText())) {
            violations += "madre-app/build.gradle.kts: application compiles against concrete owner-interaction Module"
        }
        fileTree(rootDir) { include("madre-app/src/main/**/*.java") }.forEach { source ->
            val text = source.readText()
            listOf(
                "LlamaCppReasoningCapability",
                "LlamaCppUnixSocketReasoningCapability",
                "OpenAiCompatibleReasoningCapability",
                "LlamaCppConfiguration",
                "LlamaCppUnixSocketConfiguration",
                "OpenAiCompatibleConfiguration"
            ).forEach { concrete ->
                if (text.contains(concrete)) {
                    violations += "${source.relativeTo(rootDir)}: application knows concrete reasoning adapter $concrete"
                }
            }
            if (Regex("reasoning\\.(llamacpp|openai-compatible)").containsMatchIn(text)) {
                violations += "${source.relativeTo(rootDir)}: application parses a concrete reasoning-provider namespace"
            }
            if (text.contains("io.github.didacll.madre.interaction")
                    || text.contains("OwnerInteractionModule")) {
                violations += "${source.relativeTo(rootDir)}: application compiles against concrete owner-interaction Module"
            }
            listOf(
                "io.github.didacll.madre.owner-interaction",
                "standard-prompt",
                "fast-lane",
                "collect-background",
                "owner-prompt",
                "background-collection-request",
                "durable-background-write",
                "acknowledge-completed-background"
            ).forEach { concreteIdentity ->
                if (text.contains(concreteIdentity)) {
                    violations += "${source.relativeTo(rootDir)}: application hard-codes owner-interaction identity $concreteIdentity"
                }
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
