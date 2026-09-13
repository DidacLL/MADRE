package io.github.didacll.madre.websearch;

import java.util.Objects;

/** Module-owned interpretation of one public source returned by search. */
public record ResearchSource(String title, String url, String excerpt) {
    public ResearchSource {
        if (Objects.requireNonNull(title, "title").isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (Objects.requireNonNull(url, "url").isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        title = title.strip();
        url = url.strip();
        excerpt = Objects.requireNonNull(excerpt, "excerpt").strip();
    }
}
