package minisearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class SegmentCountExperiment {
    private static final int TOTAL_DOCUMENTS = 100_000;

    public static void main(String[] args) throws IOException {
        List<Integer> segmentCounts = args.length == 0
                ? List.of(1, 10, 100, 1_000)
                : List.of(Integer.parseInt(args[0]));
        for (int segmentCount : segmentCounts) {
            Path segmentsDirectory = Files.createTempDirectory("minisearch-segment-count-");
            try {
                buildSegments(segmentsDirectory, segmentCount);

                long start = System.nanoTime();
                SegmentedSearchEngine searchEngine = new SegmentedSearchEngine(segmentsDirectory);
                long loadTime = System.nanoTime() - start;

                for (int i = 0; i < 3; i++) {
                    searchEngine.search("rare", 10);
                    searchEngine.search("common", 10);
                }

                double rareQueryTime = averageMillis(() -> searchEngine.search("rare", 10));
                double commonQueryTime = averageMillis(() -> searchEngine.search("common", 10));
                if (searchEngine.search("rare", 10).size() != 1
                        || searchEngine.search("common", 10).size() != 10) {
                    throw new IllegalStateException("Unexpected search result count");
                }
                System.out.printf("%d segments, %d docs/segment, rare: %.3f ms, common: %.3f ms, load: %.3f ms%n",
                        segmentCount,
                        TOTAL_DOCUMENTS / segmentCount,
                        rareQueryTime,
                        commonQueryTime,
                        loadTime / 1_000_000.0);
            } finally {
                deleteDirectory(segmentsDirectory);
            }
        }
    }

    private static void buildSegments(Path segmentsDirectory, int segmentCount) throws IOException {
        SegmentedSearchEngine searchEngine = new SegmentedSearchEngine(segmentsDirectory);
        int documentsPerSegment = TOTAL_DOCUMENTS / segmentCount;
        for (int segment = 0; segment < segmentCount; segment++) {
            int firstDocumentId = segment * documentsPerSegment + 1;
            int lastDocumentId = firstDocumentId + documentsPerSegment;
            for (int id = firstDocumentId; id < lastDocumentId; id++) {
                searchEngine.add(new Document(
                        id,
                        "Document " + id,
                        (id == 1 ? "rare " : "") + "common token%06d".formatted(id)));
            }
            searchEngine.flush();
        }
    }

    private static double averageMillis(Runnable operation) {
        long totalElapsed = 0;
        for (int i = 0; i < 5; i++) {
            long start = System.nanoTime();
            operation.run();
            totalElapsed += System.nanoTime() - start;
        }
        return totalElapsed / 5_000_000.0;
    }

    private static void deleteDirectory(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
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
