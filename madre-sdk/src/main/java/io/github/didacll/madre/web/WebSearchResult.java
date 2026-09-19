package io.github.didacll.madre.web;

import java.util.List;

/** Ordinary search result awaiting Module-owned interpretation. */
public record WebSearchResult(List<WebSearchHit> hits) {
    public WebSearchResult { hits = List.copyOf(hits); }
}
