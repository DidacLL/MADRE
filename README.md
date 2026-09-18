# MADRE

MADRE is an Owner-sovereign Java environment for modular semantic software and heterogeneous inference. Product meaning and boundaries live in [MADRE.md](MADRE.md).

## Current foundation

The active Gradle build has six projects:

- `madre-sdk`: Security Algebra, Modules, Agents, Operations, Workflows, semantic inference values, conversation/state helpers, embeddings, text generation, and SearXNG authoring utilities.
- `madre-kernel`: technical inference engines, matching, resource reservation, scheduling, retries, recovery, metrics, and technical-only persistence.
- `madre-runtime`: the installed semantic environment, CORE/default-Agent resolution, Module composition, semantic inference persistence/correlation, and launcher.
- `madre-module-owner-interaction`: the first ordinary SDK-built Module and default CORE candidate.
- `madre-inference-llamacpp`: optional local llama.cpp text engine.
- `madre-inference-openai-compatible`: optional local or remote OpenAI-compatible text engine.

Every Operation executes under an Agent. An agentless Module falls back to the default Agent of the Module assigned CORE. The inference Kernel receives technical work and transient inference input; it never receives Module, Agent, Operation, Material, Algebra, plan, continuation, or semantic-quality concepts, and it never persists prompt or result content.

## Build and run

JDK 21 is required. On Windows:

```powershell
.\gradlew.bat --no-daemon clean build :madre-runtime:installDist
.\madre-runtime\build\install\madre\bin\madre.bat
```

On Linux:

```bash
./gradlew --no-daemon clean build :madre-runtime:installDist
./madre-runtime/build/install/madre/bin/madre
```

With no engine properties, MADRE starts with zero inference engines. Optional examples:

```text
-Dmadre.llamacpp.endpoint=http://127.0.0.1:8080/
-Dmadre.llamacpp.model=local-model

-Dmadre.openai.endpoint=http://127.0.0.1:8081/v1/
-Dmadre.openai.model=compatible-model
-Dmadre.openai.local=true
```

Runtime state defaults to `.madre-state`; override it with `-Dmadre.state=<path>`. Provider credentials are explicit (`-Dmadre.openai.key=...`) and are not inferred or persisted by the Kernel.

See [docs/implementation-baseline.md](docs/implementation-baseline.md) for the short executable baseline.
