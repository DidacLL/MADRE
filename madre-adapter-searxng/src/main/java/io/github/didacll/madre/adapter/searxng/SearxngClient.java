package io.github.didacll.madre.adapter.searxng;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.didacll.madre.web.WebSearchCommand;
import io.github.didacll.madre.web.WebSearchHit;
import io.github.didacll.madre.web.WebSearchResult;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Ordinary reusable Java search client. It is not a Kernel reasoning mechanism. */
public final class SearxngClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SearxngConfiguration configuration;
    private final HttpClient client;

    public SearxngClient(SearxngConfiguration configuration) {
        this(configuration, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    SearxngClient(SearxngConfiguration configuration, HttpClient client) {
        this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
        this.client = java.util.Objects.requireNonNull(client, "client");
    }

    /** Lightweight endpoint observation for application code that needs it. */
    public boolean available() {
        HttpRequest request = HttpRequest.newBuilder(healthUri())
                .timeout(configuration.timeout()).header("Accept", "text/plain").GET().build();
        try {
            HttpResponse<Void> response = client.send(request,
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    public WebSearchResult search(WebSearchCommand command) throws IOException,
            InterruptedException {
        java.util.Objects.requireNonNull(command, "command");
        HttpRequest request = HttpRequest.newBuilder(searchUri(command.query()))
                .timeout(configuration.timeout()).header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("SearXNG returned HTTP " + response.statusCode());
        }
        return parse(response.body(), command.maximumResults());
    }

    private URI searchUri(String query) {
        String base = configuration.endpoint().toString();
        String separator = base.contains("?") ? "&" : "?";
        return URI.create(base + separator + "q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&format=json");
    }

    private URI healthUri() {
        URI endpoint = configuration.endpoint();
        String path = endpoint.getPath();
        String parent;
        if (path == null || path.isBlank() || "/".equals(path)) {
            parent = "/";
        } else {
            int slash = path.lastIndexOf('/');
            parent = path.substring(0, slash + 1);
        }
        try {
            return new URI(endpoint.getScheme(), endpoint.getUserInfo(), endpoint.getHost(),
                    endpoint.getPort(), parent + "healthz", null, null);
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalStateException(
                    "validated SearXNG endpoint cannot form health URI", exception);
        }
    }

    private static WebSearchResult parse(String body, int maximumResults) throws IOException {
        try {
            JsonNode root = JSON.readTree(body);
            JsonNode results = root.get("results");
            if (results == null || !results.isArray()) {
                throw new IOException("SearXNG response has no results array");
            }
            List<WebSearchHit> hits = new ArrayList<>();
            for (JsonNode item : results) {
                if (hits.size() >= maximumResults) break;
                JsonNode title = item.get("title");
                JsonNode url = item.get("url");
                if (title == null || !title.isTextual() || title.textValue().isBlank()
                        || url == null || !url.isTextual() || url.textValue().isBlank()) {
                    continue;
                }
                JsonNode content = item.get("content");
                hits.add(new WebSearchHit(title.textValue(), url.textValue(),
                        content != null && content.isTextual() ? content.textValue() : ""));
            }
            return new WebSearchResult(hits);
        } catch (com.fasterxml.jackson.core.JsonProcessingException
                | IllegalArgumentException exception) {
            throw new IOException("invalid SearXNG JSON response", exception);
        }
    }
}
