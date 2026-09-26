package io.github.didacll.madre.kernel.client;

public sealed interface HttpHeader permits LiteralHttpHeader, EnvironmentHttpHeader {
    String name();
}
