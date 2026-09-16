package io.github.didacll.madre.adapter.searxng;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Ordinary application-side configuration for one SearXNG search endpoint. */
public record SearxngConfiguration(URI endpoint, Duration timeout) {
    public SearxngConfiguration {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(timeout, "timeout");
        if (!endpoint.isAbsolute() || endpoint.getHost() == null
                || !("http".equalsIgnoreCase(endpoint.getScheme())
                        || "https".equalsIgnoreCase(endpoint.getScheme()))) {
            throw new IllegalArgumentException("endpoint must be an absolute HTTP(S) URI");
        }
        if (endpoint.getFragment() != null) {
            throw new IllegalArgumentException("endpoint must not contain a fragment");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
