package minisearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ConcurrentQueryExperiment {
    private static final int DOCUMENT_COUNT = 10_000;
    private static final int QUERIES_PER_THREAD = 100;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("minisearch-concurrent-");
        try {
            SegmentedSearchEngine searchEngine = buildEngine(directory);
            for (int threads : new int[] {1, 4, 8}) {
                System.out.printf("%d query threads: %.1f queries/s%n", threads, throughput(searchEngine, threads));
            }

            Latency baseline = latency(searchEngine, false);
            Latency whileFlush = latency(searchEngine, true);
            System.out.printf("stable query latency: average %.3f ms, max %.3f ms%n",
                    baseline.averageMillis(), baseline.maxMillis());
            System.out.printf("during indexing and flush: average %.3f ms, max %.3f ms%n",
                    whileFlush.averageMillis(), whileFlush.maxMillis());
        } finally {
            deleteDirectory(directory);
        }
    }

    private static SegmentedSearchEngine buildEngine(Path directory) throws IOException {
        IndexedSearchEngine index = new IndexedSearchEngine();
        for (int id = 1; id <= DOCUMENT_COUNT; id++) {
            index.add(new Document(id, "Document " + id, "java distributed systems token" + id));
        }
        new SegmentStorage().write(index, directory.resolve("segment-000001.bin"));
        return new SegmentedSearchEngine(directory);
    }

    private static double throughput(SegmentedSearchEngine searchEngine, int threads) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<?>[] tasks = new Future<?>[threads];
            for (int thread = 0; thread < threads; thread++) {
                tasks[thread] = executor.submit(() -> {
                    start.await();
                    for (int query = 0; query < QUERIES_PER_THREAD; query++) {
                        searchEngine.search("java", 10);
                    }
                    return null;
                });
            }
            long startNanos = System.nanoTime();
            start.countDown();
            for (Future<?> task : tasks) {
                task.get();
            }
            return threads * QUERIES_PER_THREAD / ((System.nanoTime() - startNanos) / 1_000_000_000.0);
        }
    }

    private static Latency latency(SegmentedSearchEngine searchEngine, boolean writeAndFlush) throws Exception {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> writer = writeAndFlush ? executor.submit(() -> {
                start.await();
                for (int id = DOCUMENT_COUNT + 1; id <= DOCUMENT_COUNT + 100; id++) {
                    searchEngine.add(new Document(id, "Writer " + id, "java writer token" + id));
                }
                searchEngine.flush();
                return null;
            }) : null;

            start.countDown();
            long totalNanos = 0;
            long maxNanos = 0;
            for (int query = 0; query < QUERIES_PER_THREAD; query++) {
                long startNanos = System.nanoTime();
                searchEngine.search("java", 10);
                long elapsed = System.nanoTime() - startNanos;
                totalNanos += elapsed;
                maxNanos = Math.max(maxNanos, elapsed);
            }
            if (writer != null) {
                writer.get();
            }
            return new Latency(totalNanos / (double) QUERIES_PER_THREAD / 1_000_000.0, maxNanos / 1_000_000.0);
        }
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

    private record Latency(double averageMillis, double maxMillis) {
    }
}
