package io.github.didacll.madre.kernel.capability;

import java.util.Objects;

/** Nominal command/result pair and durable codecs for one physical extension contract. */
public record PhysicalContract<C, R>(String id, Class<C> commandType, Class<R> resultType,
        PhysicalCodec<C> commandCodec, PhysicalCodec<R> resultCodec) {
    public PhysicalContract {
        if (Objects.requireNonNull(id, "id").isBlank()) throw new IllegalArgumentException("id must not be blank");
        id = id.strip();
        Objects.requireNonNull(commandType, "commandType"); Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(commandCodec, "commandCodec"); Objects.requireNonNull(resultCodec, "resultCodec");
    }
}
