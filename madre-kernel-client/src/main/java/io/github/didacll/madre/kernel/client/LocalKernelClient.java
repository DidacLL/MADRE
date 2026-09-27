package io.github.didacll.madre.kernel.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class LocalKernelClient implements KernelClient, AutoCloseable {
    private static final int MAX_REQUEST_BYTES = 1_100_000;
    private final URI baseUri;
    private final HttpClient http;
    private final ObjectMapper json;

    public LocalKernelClient(URI baseUri) {
        this.baseUri = normalize(Objects.requireNonNull(baseUri, "baseUri"));
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public WorkId submit(PhysicalInferenceRequest request) {
        SubmissionResponse response = read(
                send("POST", "/v1/work", write(request)),
                SubmissionResponse.class,
                200);
        return new WorkId(response.workId());
    }

    @Override
    public WorkInspection inspect(WorkId workId) {
        return read(send("GET", "/v1/work/" + workId.value() + "/inspect", null), WorkInspection.class, 200);
    }

    @Override
    public WorkResult result(WorkId workId) {
        HttpResponse<String> response = send("GET", "/v1/work/" + workId.value() + "/result", null);
        if (response.statusCode() == 200 || response.statusCode() == 409) {
            return read(response, WorkResult.class, response.statusCode());
        }
        throw failure(response);
    }

    @Override
    public WorkState cancel(WorkId workId) {
        CancelResponse response = read(
                send("POST", "/v1/work/" + workId.value() + "/cancel", ""),
                CancelResponse.class,
                200);
        return response.state();
    }

    @Override
    public boolean release(WorkId workId) {
        ReleaseResponse response = read(
                send("POST", "/v1/work/" + workId.value() + "/release", ""),
                ReleaseResponse.class,
                200);
        return response.released();
    }

    @Override
    public List<CapabilitySnapshot> capabilities() {
        return read(send("GET", "/v1/capabilities", null), new TypeReference<>() {}, 200);
    }

    @Override
    public List<CapabilitySnapshot> refreshCapabilities() {
        return read(send("POST", "/v1/capabilities/refresh", ""), new TypeReference<>() {}, 200);
    }

    private HttpResponse<String> send(String method, String path, String body) {
        try {
            HttpRequest.BodyPublisher publisher;
            HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json");
            if (body == null) {
                publisher = HttpRequest.BodyPublishers.noBody();
            } else {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_REQUEST_BYTES) {
                    throw new KernelClientException("request exceeds local control-plane bound", -1);
                }
                publisher = HttpRequest.BodyPublishers.ofByteArray(bytes);
                builder.header("Content-Type", "application/json");
            }
            HttpRequest request = builder.method(method, publisher).build();
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new KernelClientException("Kernel request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KernelClientException("Kernel request interrupted", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new KernelClientException("failed to encode Kernel request", exception);
        }
    }

    private <T> T read(HttpResponse<String> response, Class<T> type, int expectedStatus) {
        if (response.statusCode() != expectedStatus) {
            throw failure(response);
        }
        try {
            return json.readValue(response.body(), type);
        } catch (JsonProcessingException exception) {
            throw new KernelClientException("failed to decode Kernel response", exception);
        }
    }

    private <T> T read(HttpResponse<String> response, TypeReference<T> type, int expectedStatus) {
        if (response.statusCode() != expectedStatus) {
            throw failure(response);
        }
        try {
            return json.readValue(response.body(), type);
        } catch (JsonProcessingException exception) {
            throw new KernelClientException("failed to decode Kernel response", exception);
        }
    }

    private static KernelClientException failure(HttpResponse<String> response) {
        return new KernelClientException(
                "Kernel returned HTTP " + response.statusCode() + ": " + response.body(),
                response.statusCode());
    }

    private static URI normalize(URI uri) {
        String value = uri.toString();
        return URI.create(value.endsWith("/") ? value : value + "/");
    }

    @Override
    public void close() {
        // java.net.http.HttpClient owns no per-client closeable transport in Java 21.
    }

    private record SubmissionResponse(String workId) { }
    private record CancelResponse(WorkState state) { }
    private record ReleaseResponse(boolean released) { }
}
