package minisearch;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Path indexDirectory = Path.of(System.getenv().getOrDefault("INDEX_DIR", "data/segments"));
        SearchHttpServer server = new SearchHttpServer(new SegmentedSearchEngine(indexDirectory), port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.printf("MiniSearch listening on http://0.0.0.0:%d%n", server.port());
        Thread.currentThread().join();
    }
}
