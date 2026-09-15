package io.github.didacll.madre.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TextEmbeddingContractTest {
    @Test void roundTripsSpaceQualifiedEmbeddingValues() {
        EmbeddingSpace space = new EmbeddingSpace("example.model/v1", 3);
        TextEmbeddingCommand command = new TextEmbeddingCommand("portable text", space);
        TextEmbeddingResult result = new TextEmbeddingResult(space, List.of(0.1, -0.2, 0.3));

        assertEquals(command, TextEmbeddingCodecs.decodeCommand(
                TextEmbeddingCodecs.encodeCommand(command)));
        assertEquals(result, TextEmbeddingCodecs.decodeResult(
                TextEmbeddingCodecs.encodeResult(result)));
    }

    @Test void rejectsVectorFromWrongDimensionality() {
        EmbeddingSpace space = new EmbeddingSpace("example.model/v1", 3);
        assertThrows(IllegalArgumentException.class,
                () -> new TextEmbeddingResult(space, List.of(0.1, 0.2)));
    }
}
