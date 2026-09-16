package io.github.didacll.madre.text;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Stable version-one byte representation for durable text-inference work. */
public final class TextInferenceCodecs {
    public static final String CONTRACT_ID = "madre.text-inference.v1";
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private TextInferenceCodecs() { }

    public static byte[] encodeCommand(TextInferenceCommand value) { return encode(value); }
    public static TextInferenceCommand decodeCommand(byte[] bytes) { return decode(bytes, TextInferenceCommand.class); }
    public static byte[] encodeResult(TextInferenceResult value) { return encode(value); }
    public static TextInferenceResult decodeResult(byte[] bytes) { return decode(bytes, TextInferenceResult.class); }

    private static byte[] encode(Object value) {
        try { return JSON.writeValueAsBytes(value); }
        catch (java.io.IOException exception) { throw new IllegalArgumentException("cannot encode text-inference value", exception); }
    }
    private static <T> T decode(byte[] bytes, Class<T> type) {
        try { return JSON.readValue(bytes, type); }
        catch (java.io.IOException exception) { throw new IllegalArgumentException("cannot decode text-inference value", exception); }
    }
}
