package io.github.didacll.madre.kernel.reasoning;

import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import java.util.Objects;

/** Typed computation/result pair and durable codecs for one reasoning mechanism family. */
public record ReasoningContract<R, C extends ReasoningComputation<R>>(
        String id, Class<C> computationType, Class<R> resultType,
        ReasoningCodec<C> computationCodec, ReasoningCodec<R> resultCodec) {
    public ReasoningContract {
        if (Objects.requireNonNull(id, "id").isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        id = id.strip();
        Objects.requireNonNull(computationType, "computationType");
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(computationCodec, "computationCodec");
        Objects.requireNonNull(resultCodec, "resultCodec");
    }
}
