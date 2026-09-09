package minisearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PubMedBenchmark {
    private static final int DOCUMENT_LIMIT = 100_000;
    private static final int WARMUPS = 3;
    private static final int MEASURED_RUNS = 100;

    public static void main(String[] args) throws Exception {
        Path corpusDirectory = Path.of(args.length == 0 ? "data/pubmed-baseline" : args[0]);
        long sourceSize = sourceSize(corpusDirectory);
        long start = System.nanoTime();
        List<Document> documents = new PubMedBaselineLoader().load(corpusDirectory, DOCUMENT_LIMIT);
        double loadSourceMillis = elapsedMillis(start);
        if (documents.size() < DOCUMENT_LIMIT) {
            throw new IllegalStateException("Expected " + DOCUMENT_LIMIT + " documents but found " + documents.size());
        }

        IndexedSearchEngine index = new IndexedSearchEngine();
        start = System.nanoTime();
        for (Document document : documents) {
            index.add(document);
        }
        double buildMillis = elapsedMillis(start);

        Path indexPath = Files.createTempFile("minisearch-pubmed-", ".bin");
        try {
            IndexStorage storage = new IndexStorage();
            start = System.nanoTime();
            storage.save(index, indexPath);
            double saveMillis = elapsedMillis(start);
            long indexSize = Files.size(indexPath);

            start = System.nanoTime();
            IndexedSearchEngine loaded = storage.load(indexPath);
            double loadMillis = elapsedMillis(start);

            System.out.printf("dataset: PubMed 2026 baseline, documents: %d, source files: %.3f MB%n",
                    documents.size(), sourceSize / 1_000_000.0);
            System.out.printf("machine: %s %s, processors: %d, JDK: %s%n",
                    System.getProperty("os.name"), System.getProperty("os.arch"),
                    Runtime.getRuntime().availableProcessors(), System.getProperty("java.version"));
            System.out.printf("source load: %.3f ms%n", loadSourceMillis);
            System.out.printf("index build: %.3f ms, throughput: %.1f docs/s%n",
                    buildMillis, documents.size() / (buildMillis / 1_000.0));
            System.out.printf("persist: %.3f ms, index size: %.3f MB, startup load: %.3f ms%n",
                    saveMillis, indexSize / 1_000_000.0, loadMillis);
            reportLatency("ranked cancer", () -> loaded.search("cancer", 10));
            reportLatency("phrase randomized controlled", () -> loaded.searchPhrase("randomized controlled"));
            reportLatency("boolean cancer AND therapy", () -> loaded.searchAnd("cancer", "therapy"));
            reportLatency("autocomplete cardio", () -> loaded.suggest("cardio", 10));
            System.out.printf("concurrent ranked throughput (4 readers): %.1f queries/s%n", throughput(loaded, 4));
        } finally {
            Files.deleteIfExists(indexPath);
        }
    }

    private static void reportLatency(String name, Runnable operation) {
        for (int run = 0; run < WARMUPS; run++) {
            operation.run();
        }
        long[] samples = new long[MEASURED_RUNS];
        for (int run = 0; run < MEASURED_RUNS; run++) {
            long start = System.nanoTime();
            operation.run();
            samples[run] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        System.out.printf("%s latency: p50 %.3f ms, p95 %.3f ms, p99 %.3f ms%n", name,
                nanosToMillis(percentile(samples, 0.50)),
                nanosToMillis(percentile(samples, 0.95)),
                nanosToMillis(percentile(samples, 0.99)));
    }

    private static double throughput(IndexedSearchEngine index, int readers) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(readers)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int reader = 0; reader < readers; reader++) {
                futures.add(executor.submit((Callable<Integer>) () -> {
                    start.await();
                    for (int run = 0; run < MEASURED_RUNS; run++) {
                        index.search("cancer", 10);
                    }
                    return MEASURED_RUNS;
                }));
            }
            long startNanos = System.nanoTime();
            start.countDown();
            int queries = 0;
            for (Future<Integer> future : futures) {
                queries += future.get();
            }
            return queries / ((System.nanoTime() - startNanos) / 1_000_000_000.0);
        }
    }

    private static long sourceSize(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".xml.gz"))
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .sum();
        }
    }

    private static long percentile(long[] values, double percentile) {
        return values[(int) Math.ceil(percentile * values.length) - 1];
    }

    private static double elapsedMillis(long start) {
        return nanosToMillis(System.nanoTime() - start);
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }
}
