# MADRE

MADRE is an owner-sovereign modular environment for using local and external
inference or deterministic mechanisms while controlling which information can reach
them.

The repository is at an intentional clean architecture checkpoint. The prior Python
prototype has been removed because its public contracts contradicted the MADRE model.
There is currently no runnable MADRE distribution and no test Module standing in for
one.

Start with:

- [MADRE.md](MADRE.md) for product meaning;
- [Security Algebra](docs/architecture/MADRE-security-algebra.md) for the standalone
  composition model;
- [Platform Architecture](docs/architecture/MADRE-platform-architecture.md) for
  responsibility boundaries;
- [Module SDK and Interoperability](docs/architecture/MADRE-agent-interoperability.md)
  for the public object model;
- [Physical Execution Contract](docs/architecture/MADRE-execution-contract.md) for
  Module-to-Kernel-to-Capability execution;
- [Master Development Plan](docs/master-development-plan.md) for the complete Java 21
  implementation and real local acceptance path.

The next usable release must include the public SDK, Kernel runtime, live Module
registry, llama.cpp and OpenAI-compatible text-inference connectors, and the shipped
ordinary Module assigned to CORE with standard-prompt and fast-lane Operations.
