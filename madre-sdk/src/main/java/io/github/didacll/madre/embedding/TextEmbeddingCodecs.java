package io.github.didacll.madre.embedding;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Stable byte representation for text-embedding values. */
public final class TextEmbeddingCodecs {
    public static final String CONTRACT_ID = "madre.text-embedding.v1";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private TextEmbeddingCodecs() { }
    public static byte[] encodeCommand(TextEmbeddingCommand value) { return encode(value); }
    public static TextEmbeddingCommand decodeCommand(byte[] bytes) { return decode(bytes, TextEmbeddingCommand.class); }
    public static byte[] encodeResult(TextEmbeddingResult value) { return encode(value); }
    public static TextEmbeddingResult decodeResult(byte[] bytes) { return decode(bytes, TextEmbeddingResult.class); }
    private static byte[] encode(Object value) {
        try { return JSON.writeValueAsBytes(value); }
        catch (java.io.IOException failure) { throw new IllegalArgumentException("cannot encode text-embedding value", failure); }
    }
    private static <T> T decode(byte[] bytes, Class<T> type) {
        try { return JSON.readValue(bytes, type); }
        catch (java.io.IOException failure) { throw new IllegalArgumentException("cannot decode text-embedding value", failure); }
    }
}
