package io.github.didacll.madre.web;

import java.util.List;

/** Physical web-search output independent of Module interpretation. */
public record WebSearchResult(List<WebSearchHit> hits) {
    public WebSearchResult {
        hits = List.copyOf(hits);
    }
}
