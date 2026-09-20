package io.github.didacll.madre.kernel.client;

import java.util.Objects;

public record WorkRequest(String workType, byte[] input) {
    public WorkRequest {
        Objects.requireNonNull(workType, "workType");
        Objects.requireNonNull(input, "input");
        if (workType.isBlank()) {
            throw new IllegalArgumentException("workType must not be blank");
        }
        if (input.length > Protocol.MAX_C1_TEXT_GENERATION_OPAQUE_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "C1 text-generation/v1 input exceeds the 1 MiB bounded payload limit; streaming/spooling is deferred");
        }
        input = input.clone();
    }

    @Override
    public byte[] input() {
        return input.clone();
    }
}
