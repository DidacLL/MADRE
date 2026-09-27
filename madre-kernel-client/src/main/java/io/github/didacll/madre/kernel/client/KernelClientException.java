package io.github.didacll.madre.kernel.client;

public final class KernelClientException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final KernelIpcErrorCode errorCode;

    public KernelClientException(String message, KernelIpcErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public KernelClientException(String message, KernelIpcErrorCode errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public KernelIpcErrorCode errorCode() {
        return errorCode;
    }
}
