package io.github.didacll.madre.kernel.client;

import java.time.Duration;

final class KernelProtocol {
    static final int VERSION = 2;
    static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    static final int MAX_FRAME_BYTES = (8 * MAX_PAYLOAD_BYTES) + (64 * 1024);
    static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    private KernelProtocol() {
    }
}
