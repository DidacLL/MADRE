package io.github.didacll.madre.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reusable bounded search client. It never creates inference Kernel work. */
public final class SearxngClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SearxngConfiguration configuration;
    private final HttpClient client;
    public SearxngClient(SearxngConfiguration configuration) {
        this(configuration, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build());
    }
    SearxngClient(SearxngConfiguration configuration, HttpClient client) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.client = Objects.requireNonNull(client, "client");
    }
    public boolean available() {
        HttpRequest request = HttpRequest.newBuilder(healthUri()).timeout(configuration.timeout()).GET().build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException failure) { return false; }
    }
    public WebSearchResult search(WebSearchCommand command) throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command");
        HttpRequest request = HttpRequest.newBuilder(searchUri(command.query()))
                .timeout(configuration.timeout()).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("SearXNG returned HTTP " + response.statusCode());
        return parse(response.body(), command.maximumResults());
    }
    private URI searchUri(String query) {
        String base = configuration.endpoint().toString();
        return URI.create(base + (base.contains("?") ? "&" : "?") + "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&format=json");
    }
    private URI healthUri() {
        URI endpoint = configuration.endpoint();
        String path = endpoint.getPath();
        String parent = path == null || path.isBlank() || "/".equals(path) ? "/" : path.substring(0, path.lastIndexOf('/') + 1);
        try { return new URI(endpoint.getScheme(), endpoint.getUserInfo(), endpoint.getHost(), endpoint.getPort(), parent + "healthz", null, null); }
        catch (java.net.URISyntaxException failure) { throw new IllegalStateException("validated SearXNG endpoint cannot form health URI", failure); }
    }
    private static WebSearchResult parse(String body, int maximumResults) throws IOException {
        try {
            JsonNode results = JSON.readTree(body).get("results");
            if (results == null || !results.isArray()) throw new IOException("SearXNG response has no results array");
            List<WebSearchHit> hits = new ArrayList<>();
            for (JsonNode item : results) {
                if (hits.size() >= maximumResults) break;
                JsonNode title = item.get("title");
                JsonNode url = item.get("url");
                if (title == null || !title.isTextual() || title.textValue().isBlank() || url == null || !url.isTextual() || url.textValue().isBlank()) continue;
                JsonNode content = item.get("content");
                hits.add(new WebSearchHit(title.textValue(), url.textValue(), content != null && content.isTextual() ? content.textValue() : ""));
            }
            return new WebSearchResult(hits);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException failure) {
            throw new IOException("invalid SearXNG JSON response", failure);
        }
    }
}
