package io.github.didacll.madre.kernel.client;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProcessInvocation(
        InvocationId id,
        String executable,
        List<String> arguments,
        byte[] stdinPayload,
        Optional<String> targetIdentity) implements ConcretePhysicalInvocation {

    public ProcessInvocation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(stdinPayload, "stdinPayload");
        Objects.requireNonNull(targetIdentity, "targetIdentity");
        validateWireText(executable, "executable", false);
        if (arguments.size() > 128) throw new IllegalArgumentException("arguments must contain at most 128 entries");
        for (String argument : arguments) {
            Objects.requireNonNull(argument, "arguments entry");
            validateWireText(argument, "argument", true);
        }
        if (stdinPayload.length > Protocol.MAX_BOUNDED_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("stdinPayload exceeds the 1 MiB bounded physical payload limit");
        }
        targetIdentity.ifPresent(value -> validateWireText(value, "targetIdentity", true));
        arguments = List.copyOf(arguments);
        stdinPayload = stdinPayload.clone();
    }

    public ProcessInvocation(InvocationId id, String executable, List<String> arguments, byte[] stdinPayload) {
        this(id, executable, arguments, stdinPayload, Optional.empty());
    }

    @Override public byte[] stdinPayload() { return stdinPayload.clone(); }
    @Override public byte[] requestPayload() { return stdinPayload(); }
    @Override public InvocationKind kind() { return InvocationKind.PROCESS; }

    private static void validateWireText(String value, String field, boolean allowEmpty) {
        if ((!allowEmpty && value.isBlank()) || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " must be single-line text");
        }
    }
}
