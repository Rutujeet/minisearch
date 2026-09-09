package minisearch;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A small JSON-over-HTTP boundary for a {@link SegmentedSearchEngine}. */
public final class SearchHttpServer implements AutoCloseable {
    private final Gson json = new Gson();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final SegmentedSearchEngine engine;

    public SearchHttpServer(SegmentedSearchEngine engine, int port) throws IOException {
        this.engine = engine;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/documents", this::documents);
        server.createContext("/search", this::search);
        server.createContext("/suggest", this::suggest);
        server.setExecutor(executor);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }

    private void documents(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/documents") && exchange.getRequestMethod().equals("POST")) {
            Document document = json.fromJson(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), Document.class);
            if (document == null || document.title() == null || document.body() == null) { error(exchange, 400, "id, title, and body are required"); return; }
            engine.add(document); respond(exchange, 201, document); return;
        }
        if (path.matches("/documents/\\d+")) {
            int id = Integer.parseInt(path.substring("/documents/".length()));
            if (exchange.getRequestMethod().equals("DELETE")) { engine.delete(id); respond(exchange, 204, null); return; }
            if (exchange.getRequestMethod().equals("PUT")) {
                Document body = json.fromJson(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), Document.class);
                if (body == null || body.title() == null || body.body() == null) { error(exchange, 400, "title and body are required"); return; }
                Document document = new Document(id, body.title(), body.body()); engine.update(document); respond(exchange, 200, document); return;
            }
        }
        error(exchange, path.startsWith("/documents") ? 405 : 404, "unsupported request");
    }

    private void search(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) { error(exchange, 405, "GET required"); return; }
        Map<String, String> query = query(exchange);
        String q = query.get("q");
        if (q == null || q.isBlank()) { error(exchange, 400, "q is required"); return; }
        respond(exchange, 200, engine.search(q, limit(query)));
    }

    private void suggest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) { error(exchange, 405, "GET required"); return; }
        Map<String, String> query = query(exchange);
        String q = query.get("q");
        if (q == null || q.isBlank()) { error(exchange, 400, "q is required"); return; }
        respond(exchange, 200, engine.suggest(q, limit(query)));
    }

    private Map<String, String> query(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) return Map.of();
        return java.util.Arrays.stream(raw.split("&")).map(part -> part.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(parts -> URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        parts -> URLDecoder.decode(parts.length == 1 ? "" : parts[1], StandardCharsets.UTF_8)));
    }

    private int limit(Map<String, String> query) throws IOException {
        try { return Math.max(0, Integer.parseInt(query.getOrDefault("limit", "10"))); }
        catch (NumberFormatException exception) { throw new IOException("limit must be an integer", exception); }
    }

    private void respond(HttpExchange exchange, int status, Object body) throws IOException {
        if (body == null) { exchange.sendResponseHeaders(status, -1); return; }
        byte[] bytes = json.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void error(HttpExchange exchange, int status, String message) throws IOException { respond(exchange, status, Map.of("error", message)); }
    @Override public void close() { server.stop(0); executor.close(); }
}
