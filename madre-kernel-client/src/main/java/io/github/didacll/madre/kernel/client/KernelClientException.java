package io.github.didacll.madre.kernel.client;

public final class KernelClientException extends RuntimeException {
    private final int statusCode;

    public KernelClientException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public KernelClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int statusCode() {
        return statusCode;
    }
}
