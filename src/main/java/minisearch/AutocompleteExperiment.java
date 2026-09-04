package minisearch;

import java.util.List;

public class AutocompleteExperiment {
    private static final List<String> PREFIXES = List.of(
            "d", "di", "dis", "dist", "distr", "distri", "distrib",
            "distribu", "distribut", "distribute", "distributed");

    public static void main(String[] args) {
        for (int vocabularySize : List.of(100_000, 500_000, 1_000_000)) {
            IndexedSearchEngine searchEngine = new IndexedSearchEngine();
            for (int index = 0; index < vocabularySize - 1; index++) {
                String term = index < 10
                        ? "distributed%03d".formatted(index)
                        : "token%06d".formatted(index);
                searchEngine.add(new Document(index + 1, "shared", term));
            }

            for (int i = 0; i < 3; i++) {
                searchEngine.suggest("distributed", 10);
                suggestWhileTyping(searchEngine);
            }

            long singleQueryElapsed = 0;
            long typingSequenceElapsed = 0;
            List<String> suggestions = List.of();
            for (int i = 0; i < 5; i++) {
                long start = System.nanoTime();
                suggestions = searchEngine.suggest("distributed", 10);
                singleQueryElapsed += System.nanoTime() - start;

                start = System.nanoTime();
                suggestWhileTyping(searchEngine);
                typingSequenceElapsed += System.nanoTime() - start;
            }

            if (suggestions.size() != 10) {
                throw new IllegalStateException("Expected ten suggestions");
            }
            System.out.printf("%d unique terms, single query: %.3f ms, typing sequence: %.3f ms%n",
                    vocabularySize,
                    singleQueryElapsed / 5_000_000.0,
                    typingSequenceElapsed / 5_000_000.0);
        }
    }

    private static void suggestWhileTyping(IndexedSearchEngine searchEngine) {
        for (String prefix : PREFIXES) {
            searchEngine.suggest(prefix, 10);
        }
    }
}
