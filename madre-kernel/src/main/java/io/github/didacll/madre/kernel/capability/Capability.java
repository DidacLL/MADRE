package io.github.didacll.madre.kernel.capability;

/** Connector to exactly one physical command/result mechanism. */
public interface Capability<C, R> {
    CapabilityManifest<C, R> manifest();
    CapabilityAvailability availability();
    R execute(C command, ExecutionContext context) throws CapabilityException;
}
