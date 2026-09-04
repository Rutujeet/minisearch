package minisearch;

import java.util.List;

public class TopResultsExperiment {
    public static void main(String[] args) {
        for (int count : List.of(1_000, 10_000, 100_000)) {
            IndexedSearchEngine searchEngine = new IndexedSearchEngine();
            for (int id = 1; id <= count; id++) {
                searchEngine.add(new Document(
                        id,
                        "Document " + id,
                        "java ".repeat(id % 10 + 1) + "filler text"));
            }

            for (int i = 0; i < 3; i++) {
                searchEngine.search("java", 10);
            }

            long totalElapsed = 0;
            List<Document> results = List.of();
            for (int i = 0; i < 5; i++) {
                long start = System.nanoTime();
                results = searchEngine.search("java", 10);
                totalElapsed += System.nanoTime() - start;
            }

            if (results.size() != 10) {
                throw new IllegalStateException("Expected ten results");
            }
            System.out.printf("%d documents, top-K heap: %.3f ms%n",
                    count, totalElapsed / 5_000_000.0);
        }
    }
}
