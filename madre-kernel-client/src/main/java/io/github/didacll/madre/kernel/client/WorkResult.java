package io.github.didacll.madre.kernel.client;

import java.util.Objects;

public record WorkResult(byte[] payload) {
    public WorkResult {
        Objects.requireNonNull(payload, "payload");
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
