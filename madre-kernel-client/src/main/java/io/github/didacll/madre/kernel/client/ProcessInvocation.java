package io.github.didacll.madre.kernel.client;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProcessInvocation(
        InvocationId id,
        String executable,
        List<String> arguments,
        Optional<String> targetIdentity) implements ConcretePhysicalInvocation {

    public ProcessInvocation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(targetIdentity, "targetIdentity");
        validateWireText(executable, "executable", false);
        if (arguments.size() > 128) {
            throw new IllegalArgumentException("arguments must contain at most 128 entries");
        }
        for (String argument : arguments) {
            Objects.requireNonNull(argument, "arguments entry");
            validateWireText(argument, "argument", true);
        }
        targetIdentity.ifPresent(value -> validateWireText(value, "targetIdentity", true));
        arguments = List.copyOf(arguments);
    }

    public ProcessInvocation(InvocationId id, String executable, List<String> arguments) {
        this(id, executable, arguments, Optional.empty());
    }

    private static void validateWireText(String value, String field, boolean allowEmpty) {
        if ((!allowEmpty && value.isBlank()) || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " must be single-line text");
        }
    }
}
