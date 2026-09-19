# MADRE — established repository invariants

This is an intentionally minimal clean-development checkpoint. It does not attempt to define semantic MADRE where the Owner has not supplied an approved lane contract.

## Authority

Owner intent and explicitly approved lane architecture are authority. Historical code, tests, PRs, commits and conventional software patterns are evidence only.

The repository was reset because previous generated implementation had begun to define architecture by inertia. Git history preserves that work for selective evidence or isolated salvage; the active tree must not teach it as current MADRE.

## Cross-lane boundary

Semantic MADRE determines intent and, where applicable, information-journey reasoning before physical execution is requested.

Lane C begins only after those semantic decisions have produced already-physical Work:

```
semantic MADRE
    |
    | determines physical requirements
    v
================ HARD BOUNDARY ================
    v
physical Work
    v
native Kernel
    v
physical execution mechanism
```

The Kernel is a physical control plane. It must not know or persist semantic MADRE concepts.

The detailed Lane C contract is `docs/architecture/lane-c-native-kernel.md`.

## Development

Keep dependencies and architecture proportional to a one-developer project. Prefer working behaviour, small explicit contracts and behavioral tests over speculative frameworks or compatibility machinery.

There is no compatibility obligation to discarded implementation. When a prior API or model conflicts with an approved lane contract, replace it rather than adapting the new design around it.
