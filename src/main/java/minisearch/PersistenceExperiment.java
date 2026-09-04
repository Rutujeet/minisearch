package minisearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PersistenceExperiment {
    public static void main(String[] args) throws IOException {
        IndexStorage storage = new IndexStorage();
        for (int count : List.of(1_000, 10_000, 100_000)) {
            IndexedSearchEngine searchEngine = new IndexedSearchEngine();
            for (int id = 1; id <= count; id++) {
                searchEngine.add(new Document(
                        id,
                        "Document " + id,
                        "java ".repeat(id % 10 + 1)
                                + "distributed systems replication token%06d".formatted(id)));
            }

            Path indexPath = Files.createTempFile("minisearch-", ".bin");
            try {
                long start = System.nanoTime();
                storage.save(searchEngine, indexPath);
                long saveTime = System.nanoTime() - start;
                long fileSize = Files.size(indexPath);

                start = System.nanoTime();
                IndexedSearchEngine loaded = storage.load(indexPath);
                long loadTime = System.nanoTime() - start;

                if (loaded.search("distributed", 10).size() != 10) {
                    throw new IllegalStateException("Loaded index did not return expected results");
                }
                System.out.printf("%d documents, save: %.3f ms, load: %.3f ms, file size: %.3f MB%n",
                        count, saveTime / 1_000_000.0, loadTime / 1_000_000.0, fileSize / 1_000_000.0);
            } finally {
                Files.deleteIfExists(indexPath);
            }
        }
    }
}
