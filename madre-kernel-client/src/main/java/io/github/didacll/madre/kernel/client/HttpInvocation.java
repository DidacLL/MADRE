package io.github.didacll.madre.kernel.client;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A concrete generic POST invocation. Kernel treats the body as opaque physical bytes. */
public record HttpInvocation(
        InvocationId id,
        URI uri,
        List<HttpHeader> headers,
        byte[] body,
        Optional<String> targetIdentity) implements ConcretePhysicalInvocation {

    public HttpInvocation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(targetIdentity, "targetIdentity");
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("HttpInvocation URI scheme must be http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("HttpInvocation URI must contain a host");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("HttpInvocation URI user-info is rejected; use explicit late-bound headers for credentials");
        }
        validateSingleLine(uri.toASCIIString(), "uri", false);
        if (headers.size() > 64) throw new IllegalArgumentException("headers must contain at most 64 entries");
        Set<String> names = new HashSet<>();
        for (HttpHeader header : headers) {
            Objects.requireNonNull(header, "headers entry");
            String folded = header.name().toLowerCase(Locale.ROOT);
            if (!names.add(folded)) throw new IllegalArgumentException("duplicate HTTP header name: " + header.name());
        }
        if (body.length > Protocol.MAX_BOUNDED_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("HTTP body exceeds the 1 MiB bounded physical payload limit");
        }
        targetIdentity.ifPresent(value -> validateSingleLine(value, "targetIdentity", true));
        headers = List.copyOf(headers);
        body = body.clone();
    }

    public HttpInvocation(InvocationId id, URI uri, List<HttpHeader> headers, byte[] body) {
        this(id, uri, headers, body, Optional.empty());
    }

    @Override public byte[] body() { return body.clone(); }
    @Override public byte[] requestPayload() { return body(); }
    @Override public InvocationKind kind() { return InvocationKind.HTTP; }

    static void validateHeaderName(String value) {
        validateSingleLine(value, "header name", false);
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (c <= 32 || c >= 127 || "()<>@,;:\"/[]?={}".indexOf(c) >= 0) {
                throw new IllegalArgumentException("invalid HTTP header name");
            }
        }
    }

    static void validateSingleLine(String value, String field, boolean allowEmpty) {
        if ((!allowEmpty && value.isBlank()) || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " must be single-line text");
        }
    }
}
