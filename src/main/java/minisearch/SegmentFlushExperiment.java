package minisearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class SegmentFlushExperiment {
    private static final int INITIAL_DOCUMENTS = 100_000;

    public static void main(String[] args) throws IOException {
        Path segmentsDirectory = Files.createTempDirectory("minisearch-segments-");
        try {
            SegmentedSearchEngine searchEngine = new SegmentedSearchEngine(segmentsDirectory);
            for (int id = 1; id <= INITIAL_DOCUMENTS; id++) {
                searchEngine.add(document(id));
            }
            searchEngine.flush();

            int nextDocumentId = INITIAL_DOCUMENTS + 1;
            for (int addedDocuments : List.of(1, 100, 1_000)) {
                for (int id = nextDocumentId; id < nextDocumentId + addedDocuments; id++) {
                    searchEngine.add(document(id));
                }
                nextDocumentId += addedDocuments;

                long start = System.nanoTime();
                searchEngine.flush();
                long flushTime = System.nanoTime() - start;
                Path segmentPath = searchEngine.segmentPaths().getLast();
                System.out.printf("+%d documents, segment flush: %.3f ms, segment size: %.3f MB%n",
                        addedDocuments, flushTime / 1_000_000.0, Files.size(segmentPath) / 1_000_000.0);
            }
        } finally {
            try (var paths = Files.walk(segmentsDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
        }
    }

    private static Document document(int id) {
        return new Document(
                id,
                "Document " + id,
                "java ".repeat(id % 10 + 1)
                        + "distributed systems replication token%06d".formatted(id));
    }
}
