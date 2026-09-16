package io.github.didacll.madre.interaction;

import io.github.didacll.madre.algebra.Sensitivity;

enum OwnerKnowledgeKind {
    OWNER_FACT(Sensitivity.S3),
    INTERACTION_PREFERENCE(Sensitivity.S2),
    ENVIRONMENT_FACT(Sensitivity.S3),
    HIGHLY_SENSITIVE(Sensitivity.S5);

    private final Sensitivity minimumSensitivity;

    OwnerKnowledgeKind(Sensitivity minimumSensitivity) {
        this.minimumSensitivity = minimumSensitivity;
    }

    Sensitivity minimumSensitivity() { return minimumSensitivity; }
}
