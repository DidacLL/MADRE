package io.github.didacll.madre.web;

import java.util.Objects;

/** One physical result item returned by an installed web-search mechanism. */
public record WebSearchHit(String title, String url, String snippet) {
    public WebSearchHit {
        if (Objects.requireNonNull(title, "title").isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (Objects.requireNonNull(url, "url").isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        title = title.strip();
        url = url.strip();
        snippet = Objects.requireNonNull(snippet, "snippet").strip();
    }
}
