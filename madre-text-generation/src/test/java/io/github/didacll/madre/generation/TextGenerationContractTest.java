package io.github.didacll.madre.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TextGenerationContractTest {
    @Test void roundTripsVersionTwoDurableValues() {
        TextGenerationCommand command = new TextGenerationCommand(List.of(
                new TextGenerationMessage(TextGenerationMessage.Role.SYSTEM, "Be concise."),
                new TextGenerationMessage(TextGenerationMessage.Role.USER, "Explain MADRE.")),
                64, List.of("END"));
        TextGenerationResult result = new TextGenerationResult("A framework.",
                TextGenerationResult.CompletionReason.STOP, 7, 3);

        assertEquals(command, TextGenerationCodecs.decodeCommand(
                TextGenerationCodecs.encodeCommand(command)));
        assertEquals(result, TextGenerationCodecs.decodeResult(
                TextGenerationCodecs.encodeResult(result)));
    }

    @Test void rejectsEmptyConversation() {
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationCommand(List.of(), 1, List.of()));
    }
}
