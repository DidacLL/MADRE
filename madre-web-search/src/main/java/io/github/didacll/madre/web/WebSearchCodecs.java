package io.github.didacll.madre.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Stable version-one byte representation for durable web-search work. */
public final class WebSearchCodecs {
    public static final String CONTRACT_ID = "madre.web-search.v1";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private WebSearchCodecs() { }

    public static byte[] encodeCommand(WebSearchCommand value) { return encode(value); }
    public static WebSearchCommand decodeCommand(byte[] bytes) {
        return decode(bytes, WebSearchCommand.class);
    }
    public static byte[] encodeResult(WebSearchResult value) { return encode(value); }
    public static WebSearchResult decodeResult(byte[] bytes) {
        return decode(bytes, WebSearchResult.class);
    }

    private static byte[] encode(Object value) {
        try {
            return JSON.writeValueAsBytes(value);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("cannot encode web-search value", exception);
        }
    }

    private static <T> T decode(byte[] bytes, Class<T> type) {
        try {
            return JSON.readValue(bytes, type);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("cannot decode web-search value", exception);
        }
    }
}
