package minisearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchHttpServerTest {
    @TempDir Path tempDir;

    @Test
    void createsSearchesUpdatesAndDeletesDocumentsOverHttp() throws Exception {
        try (SearchHttpServer server = new SearchHttpServer(new SegmentedSearchEngine(tempDir), 0)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            URI base = URI.create("http://localhost:" + server.port());
            HttpResponse<String> created = client.send(HttpRequest.newBuilder(base.resolve("/documents"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"id\":1,\"title\":\"Java\",\"body\":\"distributed systems\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(201, created.statusCode());
            HttpResponse<String> search = client.send(HttpRequest.newBuilder(base.resolve("/search?q=distributed&limit=10")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, search.statusCode()); assertTrue(search.body().contains("distributed systems"));
            HttpResponse<String> suggest = client.send(HttpRequest.newBuilder(base.resolve("/suggest?q=dist")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(suggest.body().contains("distributed"));
            assertEquals(200, client.send(HttpRequest.newBuilder(base.resolve("/documents/1"))
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"id\":9,\"title\":\"Redis\",\"body\":\"new content\"}")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertTrue(client.send(HttpRequest.newBuilder(base.resolve("/search?q=new")).GET().build(), HttpResponse.BodyHandlers.ofString()).body().contains("new content"));
            assertEquals(204, client.send(HttpRequest.newBuilder(base.resolve("/documents/1")).DELETE().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals("[]", client.send(HttpRequest.newBuilder(base.resolve("/search?q=new")).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        }
    }
}
