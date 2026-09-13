package io.github.didacll.madre.websearch;

import java.util.List;
import java.util.Objects;

/** Module-owned interpreted result of one bounded search Operation. */
public record SearchResultSet(String query, List<ResearchSource> sources) {
    public SearchResultSet {
        if (Objects.requireNonNull(query, "query").isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        query = query.strip();
        sources = List.copyOf(sources);
    }
}
