package io.github.didacll.madre.kernel.client;

import java.util.Objects;

public record WorkRequest(String workType, byte[] input) {
    public WorkRequest {
        Objects.requireNonNull(workType, "workType");
        Objects.requireNonNull(input, "input");
        if (workType.isBlank()) {
            throw new IllegalArgumentException("workType must not be blank");
        }
        input = input.clone();
    }

    @Override
    public byte[] input() {
        return input.clone();
    }
}
