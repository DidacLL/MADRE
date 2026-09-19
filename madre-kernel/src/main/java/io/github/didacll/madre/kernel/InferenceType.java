package io.github.didacll.madre.kernel;

import java.util.Objects;

/** Nominal, typed physical inference family understood by engines and work. */
public final class InferenceType<I, O> {
    private final String id;
    private final Class<I> inputType;
    private final Class<O> outputType;

    public InferenceType(String id, Class<I> inputType, Class<O> outputType) {
        this.id = requireText(id, "id");
        this.inputType = Objects.requireNonNull(inputType, "inputType");
        this.outputType = Objects.requireNonNull(outputType, "outputType");
    }

    public String id() { return id; }
    public Class<I> inputType() { return inputType; }
    public Class<O> outputType() { return outputType; }

    public I requireInput(Object input) { return inputType.cast(Objects.requireNonNull(input, "input")); }
    public O requireOutput(Object output) { return outputType.cast(Objects.requireNonNull(output, "output")); }

    @Override public boolean equals(Object other) {
        return other instanceof InferenceType<?, ?> candidate && id.equals(candidate.id)
                && inputType.equals(candidate.inputType) && outputType.equals(candidate.outputType);
    }

    @Override public int hashCode() { return Objects.hash(id, inputType, outputType); }
    @Override public String toString() { return id; }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
