package io.github.didacll.madre.adapter.searxng;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.didacll.madre.kernel.capability.Capability;
import io.github.didacll.madre.kernel.capability.CapabilityAvailability;
import io.github.didacll.madre.kernel.capability.CapabilityException;
import io.github.didacll.madre.kernel.capability.CapabilityManifest;
import io.github.didacll.madre.kernel.capability.ExecutionContext;
import io.github.didacll.madre.kernel.capability.PhysicalCodec;
import io.github.didacll.madre.kernel.capability.PhysicalContract;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import io.github.didacll.madre.web.WebSearchCodecs;
import io.github.didacll.madre.web.WebSearchCommand;
import io.github.didacll.madre.web.WebSearchHit;
import io.github.didacll.madre.web.WebSearchResult;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Physical SearXNG JSON-search connector. */
public final class SearxngCapability implements Capability<WebSearchCommand, WebSearchResult> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PhysicalContract<WebSearchCommand, WebSearchResult> CONTRACT =
            new PhysicalContract<>(WebSearchCodecs.CONTRACT_ID, WebSearchCommand.class,
                    WebSearchResult.class, new PhysicalCodec<>() {
                        @Override public byte[] encode(WebSearchCommand value) {
                            return WebSearchCodecs.encodeCommand(value);
                        }
                        @Override public WebSearchCommand decode(byte[] bytes) {
                            return WebSearchCodecs.decodeCommand(bytes);
                        }
                    }, new PhysicalCodec<>() {
                        @Override public byte[] encode(WebSearchResult value) {
                            return WebSearchCodecs.encodeResult(value);
                        }
                        @Override public WebSearchResult decode(byte[] bytes) {
                            return WebSearchCodecs.decodeResult(bytes);
                        }
                    });

    private final SearxngConfiguration configuration;
    private final HttpClient client;
    private final CapabilityManifest<WebSearchCommand, WebSearchResult> manifest;

    public SearxngCapability(SearxngConfiguration configuration) {
        this(configuration, HttpClient.newBuilder().followRedirects(
                HttpClient.Redirect.NORMAL).build());
    }

    SearxngCapability(SearxngConfiguration configuration, HttpClient client) {
        this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
        this.client = java.util.Objects.requireNonNull(client, "client");
        manifest = new CapabilityManifest<>(configuration.id(), CONTRACT,
                configuration.privacy(), Optional.of(configuration.integrity()),
                PhysicalLocation.REMOTE, configuration.expectedLatency(),
                configuration.resources());
    }

    @Override public CapabilityManifest<WebSearchCommand, WebSearchResult> manifest() {
        return manifest;
    }

    @Override public CapabilityAvailability availability() {
        return CapabilityAvailability.AVAILABLE;
    }

    @Override public WebSearchResult execute(WebSearchCommand command, ExecutionContext context)
            throws CapabilityException {
        java.util.Objects.requireNonNull(command, "command");
        java.util.Objects.requireNonNull(context, "context");
        context.requireActive();
        Duration remaining = Duration.between(Instant.now(), context.deadline());
        if (remaining.isZero() || remaining.isNegative()) {
            context.requireActive();
        }
        HttpRequest request = HttpRequest.newBuilder(searchUri(command.query()))
                .timeout(remaining)
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            context.requireActive();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CapabilityException(PhysicalFailureCategory.REMOTE_FAILURE,
                        "SearXNG returned HTTP " + response.statusCode());
            }
            return parse(response.body(), command.maximumResults());
        } catch (HttpTimeoutException exception) {
            throw new CapabilityException(PhysicalFailureCategory.TIMEOUT,
                    "SearXNG request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CapabilityException(PhysicalFailureCategory.INTERRUPTED,
                    "SearXNG request interrupted", exception);
        } catch (IOException exception) {
            throw new CapabilityException(PhysicalFailureCategory.CONNECTION,
                    "SearXNG connection failed", exception);
        }
    }

    private URI searchUri(String query) {
        String base = configuration.endpoint().toString();
        String separator = base.contains("?") ? "&" : "?";
        return URI.create(base + separator + "q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&format=json");
    }

    private static WebSearchResult parse(String body, int maximumResults)
            throws CapabilityException {
        try {
            JsonNode root = JSON.readTree(body);
            JsonNode results = root.get("results");
            if (results == null || !results.isArray()) {
                throw new CapabilityException(PhysicalFailureCategory.PROTOCOL,
                        "SearXNG response has no results array");
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
        } catch (CapabilityException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new CapabilityException(PhysicalFailureCategory.PROTOCOL,
                    "invalid SearXNG JSON response", exception);
        }
    }
}
