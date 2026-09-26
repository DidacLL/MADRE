package io.github.didacll.madre.kernel.client;

import java.util.Objects;

/** A header whose value is intentionally durable Work data. Do not use this for secrets. */
public record LiteralHttpHeader(String name, String value) implements HttpHeader {
    public LiteralHttpHeader {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        HttpInvocation.validateHeaderName(name);
        HttpInvocation.validateSingleLine(value, "literal header value", true);
    }
}
