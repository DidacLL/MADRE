package io.github.didacll.madre.kernel.client;

import java.util.Optional;

public sealed interface ConcretePhysicalInvocation permits ProcessInvocation, HttpInvocation {
    InvocationId id();
    Optional<String> targetIdentity();
    byte[] requestPayload();
    InvocationKind kind();
}
