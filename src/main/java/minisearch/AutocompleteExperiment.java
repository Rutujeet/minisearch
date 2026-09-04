package minisearch;

import java.util.List;

public class AutocompleteExperiment {
    public static void main(String[] args) {
        for (int vocabularySize : List.of(1_000, 10_000, 100_000)) {
            IndexedSearchEngine searchEngine = new IndexedSearchEngine();
            for (int index = 0; index < vocabularySize - 1; index++) {
                String term = index < 10
                        ? "distributed%03d".formatted(index)
                        : "token%06d".formatted(index);
                searchEngine.add(new Document(index + 1, "shared", term));
            }

            for (int i = 0; i < 3; i++) {
                searchEngine.suggest("distributed", 10);
            }

            long totalElapsed = 0;
            List<String> suggestions = List.of();
            for (int i = 0; i < 5; i++) {
                long start = System.nanoTime();
                suggestions = searchEngine.suggest("distributed", 10);
                totalElapsed += System.nanoTime() - start;
            }

            if (suggestions.size() != 10) {
                throw new IllegalStateException("Expected ten suggestions");
            }
            System.out.printf("%d unique terms, full vocabulary scan: %.3f ms%n",
                    vocabularySize, totalElapsed / 5_000_000.0);
        }
    }
}
