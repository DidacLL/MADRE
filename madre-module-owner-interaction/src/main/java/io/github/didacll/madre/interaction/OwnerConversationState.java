package io.github.didacll.madre.interaction;

import io.github.didacll.madre.algebra.Sensitivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable semantic conversation state owned by the shipped interaction Agent. */
record OwnerConversationState(List<OwnerConversationExchange> exchanges) {
    OwnerConversationState {
        exchanges = List.copyOf(Objects.requireNonNull(exchanges, "exchanges"));
    }

    static OwnerConversationState empty() { return new OwnerConversationState(List.of()); }

    OwnerConversationState limited(int maximumExchanges) {
        requireMaximum(maximumExchanges);
        if (exchanges.size() <= maximumExchanges) return this;
        return new OwnerConversationState(
                exchanges.subList(exchanges.size() - maximumExchanges, exchanges.size()));
    }

    OwnerConversationState remember(String ownerPrompt, Sensitivity ownerSensitivity,
            String assistantAnswer, Sensitivity assistantSensitivity, int maximumExchanges) {
        requireMaximum(maximumExchanges);
        List<OwnerConversationExchange> next = new ArrayList<>(exchanges);
        next.add(new OwnerConversationExchange(ownerPrompt, ownerSensitivity,
                assistantAnswer, assistantSensitivity));
        int firstRetained = Math.max(0, next.size() - maximumExchanges);
        return new OwnerConversationState(next.subList(firstRetained, next.size()));
    }

    private static void requireMaximum(int maximumExchanges) {
        if (maximumExchanges < 1) {
            throw new IllegalArgumentException("maximum conversation exchanges must be positive");
        }
    }
}
