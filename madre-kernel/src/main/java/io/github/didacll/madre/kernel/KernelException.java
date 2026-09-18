package io.github.didacll.madre.kernel;

public final class KernelException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public KernelException(String message) { super(message); }
    public KernelException(String message, Throwable cause) { super(message, cause); }
}
