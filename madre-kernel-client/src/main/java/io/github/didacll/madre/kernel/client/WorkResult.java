package io.github.didacll.madre.kernel.client;

public record WorkResult(WorkState state, boolean released, String result) {
}
