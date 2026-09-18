package io.github.didacll.madre.generation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Stable byte representation for text-generation values. */
public final class TextGenerationCodecs {
    public static final String CONTRACT_ID = "madre.text-generation.v2";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private TextGenerationCodecs() { }
    public static byte[] encodeCommand(TextGenerationCommand value) { return encode(value); }
    public static TextGenerationCommand decodeCommand(byte[] bytes) {
        return decode(bytes, TextGenerationCommand.class);
    }
    public static byte[] encodeResult(TextGenerationResult value) { return encode(value); }
    public static TextGenerationResult decodeResult(byte[] bytes) {
        return decode(bytes, TextGenerationResult.class);
    }
    private static byte[] encode(Object value) {
        try { return JSON.writeValueAsBytes(value); }
        catch (java.io.IOException failure) { throw new IllegalArgumentException("cannot encode text-generation value", failure); }
    }
    private static <T> T decode(byte[] bytes, Class<T> type) {
        try { return JSON.readValue(bytes, type); }
        catch (java.io.IOException failure) { throw new IllegalArgumentException("cannot decode text-generation value", failure); }
    }
}
