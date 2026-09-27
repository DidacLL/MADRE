package io.github.didacll.madre.kernel.client;

public record ConfiguredFact<T>(T value, FactProvenance provenance) {
}
