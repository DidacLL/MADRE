package io.github.didacll.madre.kernel.client;

public final class KernelProtocolException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;

    public KernelProtocolException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
