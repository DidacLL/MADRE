package io.github.didacll.madre.kernel.client;

public final class KernelClientException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public KernelClientException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public KernelClientException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public String errorCode() {
        return errorCode;
    }
}
