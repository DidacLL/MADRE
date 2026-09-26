package io.github.didacll.madre.kernel.client;

import java.util.Objects;

/**
 * A late-bound header. Only the environment-variable name and non-secret prefix/suffix are durable;
 * the environment value is resolved by Kernel immediately before the physical attempt and is not persisted.
 */
public record EnvironmentHttpHeader(String name, String environmentVariable, String prefix, String suffix)
        implements HttpHeader {
    public EnvironmentHttpHeader {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(environmentVariable, "environmentVariable");
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(suffix, "suffix");
        HttpInvocation.validateHeaderName(name);
        HttpInvocation.validateSingleLine(environmentVariable, "environmentVariable", false);
        HttpInvocation.validateSingleLine(prefix, "header prefix", true);
        HttpInvocation.validateSingleLine(suffix, "header suffix", true);
    }
}
