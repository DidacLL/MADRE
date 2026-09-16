package io.github.didacll.madre.web;

import java.util.Objects;

/** Physical web-search request independent of any Module meaning. */
public record WebSearchCommand(String query, int maximumResults) {
    public WebSearchCommand {
        if (Objects.requireNonNull(query, "query").isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        query = query.strip();
        if (maximumResults < 1 || maximumResults > 50) {
            throw new IllegalArgumentException("maximumResults must be between 1 and 50");
        }
    }
}
