package io.github.didacll.madre.sdk.codec;

/** Failure to encode or reconstruct a versioned public boundary representation. */
public final class CodecException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public CodecException(String message) { super(message); }
    public CodecException(String message, Throwable cause) { super(message, cause); }
}
