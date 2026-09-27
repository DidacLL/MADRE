package io.github.didacll.madre.kernel.client;

final class KernelProtocol {
    static final int VERSION = 1;
    static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    static final int MAX_FRAME_BYTES = (8 * MAX_PAYLOAD_BYTES) + (64 * 1024);

    private KernelProtocol() {
    }
}
