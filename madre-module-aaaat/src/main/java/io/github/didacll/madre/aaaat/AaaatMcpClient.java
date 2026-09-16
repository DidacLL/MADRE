package io.github.didacll.madre.aaaat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** AAAAT-specific stdio MCP transport kept private to this Module. */
final class AaaatMcpClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration EXIT_GRACE = Duration.ofSeconds(2);

    private final List<String> executablePrefix;
    private final Path workspace;

    AaaatMcpClient(Path executable, Path workspace) {
        this(List.of(Objects.requireNonNull(executable, "executable").toString()), workspace);
    }

    AaaatMcpClient(List<String> executablePrefix, Path workspace) {
        if (Objects.requireNonNull(executablePrefix, "executablePrefix").isEmpty()) {
            throw new IllegalArgumentException("executablePrefix must not be empty");
        }
        this.executablePrefix = List.copyOf(executablePrefix);
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    String call(String toolName, String argumentsJson) {
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(argumentsJson, "argumentsJson");
        JsonNode arguments;
        try {
            arguments = JSON.readTree(argumentsJson);
        } catch (IOException exception) {
            throw new IllegalArgumentException("AAAAT Operation input is not valid JSON", exception);
        }
        if (arguments == null || !arguments.isObject()) {
            throw new IllegalArgumentException("AAAAT Operation input must be a JSON object");
        }

        List<String> command = new ArrayList<>(executablePrefix);
        command.add("--mcp");
        command.add("--workspace");
        command.add(workspace.toString());

        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            process.getInputStream(), StandardCharsets.UTF_8))) {
                write(writer, initialize(1));
                JsonNode initialized = response(reader, 1);
                String protocolVersion = initialized.path("result").path("protocolVersion").asText();
                if (protocolVersion.isBlank()) {
                    throw new IllegalStateException("AAAAT MCP initialize response omitted protocolVersion");
                }
                ObjectNode notification = JSON.createObjectNode();
                notification.put("jsonrpc", "2.0");
                notification.put("method", "notifications/initialized");
                write(writer, notification);

                ObjectNode request = JSON.createObjectNode();
                request.put("jsonrpc", "2.0");
                request.put("id", 2);
                request.put("method", "tools/call");
                ObjectNode params = request.putObject("params");
                params.put("name", toolName);
                params.set("arguments", arguments);
                write(writer, request);

                JsonNode called = response(reader, 2);
                JsonNode result = called.path("result");
                if (result.path("isError").asBoolean(false)) {
                    throw new IllegalStateException("AAAAT MCP tool failed: " + textContent(result));
                }
                String text = textContent(result);
                JsonNode domain = JSON.readTree(text);
                if (domain == null) {
                    return "null";
                }
                return JSON.writeValueAsString(domain);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("AAAAT MCP process/protocol failure", exception);
        } finally {
            if (process != null) {
                process.destroy();
                try {
                    if (!process.waitFor(EXIT_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }
        }
    }

    private static ObjectNode initialize(int id) {
        ObjectNode request = JSON.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", "initialize");
        ObjectNode params = request.putObject("params");
        params.put("protocolVersion", "2025-06-18");
        params.putObject("capabilities");
        ObjectNode client = params.putObject("clientInfo");
        client.put("name", "madre-aaaat-module");
        client.put("version", "0.1.0");
        return request;
    }

    private static JsonNode response(BufferedReader reader, int expectedId) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            JsonNode message;
            try {
                message = JSON.readTree(line);
            } catch (IOException malformed) {
                throw new IllegalStateException("AAAAT MCP emitted malformed JSON-RPC", malformed);
            }
            if (message == null || !message.has("id") || message.path("id").asInt(-1) != expectedId) {
                continue;
            }
            if (message.has("error")) {
                throw new IllegalStateException("AAAAT MCP JSON-RPC error: " + message.get("error"));
            }
            if (!message.has("result")) {
                throw new IllegalStateException("AAAAT MCP response omitted result for id " + expectedId);
            }
            return message;
        }
        throw new IllegalStateException("AAAAT MCP process ended before response id " + expectedId);
    }

    private static String textContent(JsonNode result) {
        for (JsonNode content : result.path("content")) {
            if ("text".equals(content.path("type").asText()) && content.has("text")) {
                return content.path("text").asText();
            }
        }
        throw new IllegalStateException("AAAAT MCP tool response omitted text content");
    }

    private static void write(BufferedWriter writer, JsonNode message) throws IOException {
        writer.write(JSON.writeValueAsString(message));
        writer.newLine();
        writer.flush();
    }
}
