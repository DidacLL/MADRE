package io.github.didacll.madre.sdk.material;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Standard stateless codecs for common Java payload representations. */
public final class MaterialCodecs {
    private static final MaterialCodec<String> UTF_8_STRING = new MaterialCodec<>() {
        @Override
        public byte[] encode(String value) {
            return Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String decode(byte[] bytes) {
            return new String(Objects.requireNonNull(bytes, "bytes"), StandardCharsets.UTF_8);
        }
    };

    private MaterialCodecs() { }

    /** Returns the standard UTF-8 codec for String payloads. */
    public static MaterialCodec<String> utf8String() {
        return UTF_8_STRING;
    }
}
