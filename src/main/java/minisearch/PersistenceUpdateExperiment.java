package minisearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PersistenceUpdateExperiment {
    private static final int EXISTING_DOCUMENTS = 100_000;

    public static void main(String[] args) throws IOException {
        IndexStorage storage = new IndexStorage();
        List<Integer> updateSizes = args.length == 0
                ? List.of(1, 100, 1_000)
                : List.of(Integer.parseInt(args[0]));
        for (int addedDocuments : updateSizes) {
            IndexedSearchEngine searchEngine = buildEngine(EXISTING_DOCUMENTS);
            Path indexPath = Files.createTempFile("minisearch-update-", ".bin");
            try {
                storage.save(searchEngine, indexPath);
                for (int id = EXISTING_DOCUMENTS + 1; id <= EXISTING_DOCUMENTS + addedDocuments; id++) {
                    searchEngine.add(document(id));
                }

                long start = System.nanoTime();
                storage.save(searchEngine, indexPath);
                long reSaveTime = System.nanoTime() - start;
                long fileSize = Files.size(indexPath);

                if (storage.load(indexPath).search("token%06d".formatted(EXISTING_DOCUMENTS + addedDocuments), 1)
                        .isEmpty()) {
                    throw new IllegalStateException("Re-saved index did not contain the added document");
                }
                System.out.printf("%d existing documents, +%d: re-save %.3f ms, file size %.3f MB%n",
                        EXISTING_DOCUMENTS, addedDocuments, reSaveTime / 1_000_000.0, fileSize / 1_000_000.0);
            } finally {
                Files.deleteIfExists(indexPath);
            }
        }
    }

    private static IndexedSearchEngine buildEngine(int count) {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        for (int id = 1; id <= count; id++) {
            searchEngine.add(document(id));
        }
        return searchEngine;
    }

    private static Document document(int id) {
        return new Document(
                id,
                "Document " + id,
                "java ".repeat(id % 10 + 1)
                        + "distributed systems replication token%06d".formatted(id));
    }
}
