package io.github.didacll.madre.kernel.client;

import java.util.Optional;

public sealed interface ConcretePhysicalInvocation permits ProcessInvocation {
    InvocationId id();
    Optional<String> targetIdentity();
}
