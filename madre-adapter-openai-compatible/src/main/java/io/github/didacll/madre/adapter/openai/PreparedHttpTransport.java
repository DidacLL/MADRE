package io.github.didacll.madre.adapter.openai;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Externally prepared HTTP session boundary; MADRE owns no account mechanism. */
@FunctionalInterface
public interface PreparedHttpTransport {
    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
}
