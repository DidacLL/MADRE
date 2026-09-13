package io.github.didacll.madre.sdk.invocation;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import java.util.Objects;

/** Request to enter one target running Module's already-reachable bounded Operation. */
public record ModuleInvocation(ModuleId caller, OperationId targetOperation, Material<?> input) {
    public ModuleInvocation { Objects.requireNonNull(caller, "caller"); Objects.requireNonNull(targetOperation, "targetOperation"); Objects.requireNonNull(input, "input"); }
}
