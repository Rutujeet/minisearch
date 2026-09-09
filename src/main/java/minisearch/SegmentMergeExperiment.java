package minisearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class SegmentMergeExperiment {
    private static final int SEGMENT_COUNT = 1_000;
    private static final int DOCUMENTS_PER_SEGMENT = 100;

    public static void main(String[] args) throws IOException {
        Path segmentsDirectory = Files.createTempDirectory("minisearch-segment-merge-");
        try {
            buildSegments(segmentsDirectory);

            long start = System.nanoTime();
            SegmentedSearchEngine beforeMerge = new SegmentedSearchEngine(segmentsDirectory);
            double loadBefore = elapsedMillis(start);
            warmUp(beforeMerge);
            double rareBefore = averageMillis(() -> beforeMerge.search("rare", 10));
            double commonBefore = averageMillis(() -> beforeMerge.search("common", 10));

            start = System.nanoTime();
            beforeMerge.mergeAllSegments();
            double mergeTime = elapsedMillis(start);
            long mergedFileSize = Files.size(beforeMerge.segmentPaths().getFirst());

            start = System.nanoTime();
            SegmentedSearchEngine afterMerge = new SegmentedSearchEngine(segmentsDirectory);
            double loadAfter = elapsedMillis(start);
            warmUp(afterMerge);
            double rareAfter = averageMillis(() -> afterMerge.search("rare", 10));
            double commonAfter = averageMillis(() -> afterMerge.search("common", 10));

            if (afterMerge.search("rare", 10).size() != 1 || afterMerge.search("common", 10).size() != 10) {
                throw new IllegalStateException("Merged index returned unexpected results");
            }
            System.out.printf("before: %d segments, rare %.3f ms, common %.3f ms, load %.3f ms%n",
                    SEGMENT_COUNT, rareBefore, commonBefore, loadBefore);
            System.out.printf("merge: %.3f ms, merged file size %.3f MB, after: %d segment%n",
                    mergeTime, mergedFileSize / 1_000_000.0, afterMerge.segmentPaths().size());
            System.out.printf("after: rare %.3f ms, common %.3f ms, load %.3f ms%n",
                    rareAfter, commonAfter, loadAfter);
        } finally {
            deleteDirectory(segmentsDirectory);
        }
    }

    private static void buildSegments(Path segmentsDirectory) throws IOException {
        SegmentedSearchEngine searchEngine = new SegmentedSearchEngine(segmentsDirectory);
        for (int segment = 0; segment < SEGMENT_COUNT; segment++) {
            int firstDocumentId = segment * DOCUMENTS_PER_SEGMENT + 1;
            for (int id = firstDocumentId; id < firstDocumentId + DOCUMENTS_PER_SEGMENT; id++) {
                searchEngine.add(new Document(
                        id,
                        "Document " + id,
                        (id == 1 ? "rare " : "") + "common token%06d".formatted(id)));
            }
            searchEngine.flush();
        }
    }

    private static void warmUp(SegmentedSearchEngine searchEngine) {
        for (int index = 0; index < 3; index++) {
            searchEngine.search("rare", 10);
            searchEngine.search("common", 10);
        }
    }

    private static double averageMillis(Runnable operation) {
        long totalElapsed = 0;
        for (int index = 0; index < 5; index++) {
            long start = System.nanoTime();
            operation.run();
            totalElapsed += System.nanoTime() - start;
        }
        return totalElapsed / 5_000_000.0;
    }

    private static double elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
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
