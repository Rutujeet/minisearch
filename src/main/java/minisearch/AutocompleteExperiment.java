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
            long start = System.nanoTime();
            SortedVocabulary sortedVocabulary = searchEngine.sortedVocabulary();
            long vocabularyConstructionTime = System.nanoTime() - start;

            for (int i = 0; i < 3; i++) {
                searchEngine.suggest("distributed", 10);
                scanWhileTyping(searchEngine);
                sortedVocabulary.suggest("distributed", 10);
                sortedWhileTyping(sortedVocabulary);
                sortedVocabulary.suggest("t", 10);
            }

            long scanSingleQueryElapsed = 0;
            long scanTypingSequenceElapsed = 0;
            long sortedSingleQueryElapsed = 0;
            long sortedTypingSequenceElapsed = 0;
            long sortedBroadPrefixElapsed = 0;
            List<String> sortedSuggestions = List.of();
            List<String> broadSuggestions = List.of();
            for (int i = 0; i < 5; i++) {
                start = System.nanoTime();
                searchEngine.suggest("distributed", 10);
                scanSingleQueryElapsed += System.nanoTime() - start;

                start = System.nanoTime();
                scanWhileTyping(searchEngine);
                scanTypingSequenceElapsed += System.nanoTime() - start;

                start = System.nanoTime();
                sortedSuggestions = sortedVocabulary.suggest("distributed", 10);
                sortedSingleQueryElapsed += System.nanoTime() - start;

                start = System.nanoTime();
                sortedWhileTyping(sortedVocabulary);
                sortedTypingSequenceElapsed += System.nanoTime() - start;

                start = System.nanoTime();
                broadSuggestions = sortedVocabulary.suggest("t", 10);
                sortedBroadPrefixElapsed += System.nanoTime() - start;
            }

            if (sortedSuggestions.size() != 10 || broadSuggestions.size() != 10) {
                throw new IllegalStateException("Expected ten suggestions");
            }
            System.out.printf("%d unique terms, build: %.3f ms, scan: %.3f/%.3f ms, sorted: %.3f/%.3f ms, broad t: %.3f ms%n",
                    vocabularySize,
                    vocabularyConstructionTime / 1_000_000.0,
                    scanSingleQueryElapsed / 5_000_000.0,
                    scanTypingSequenceElapsed / 5_000_000.0,
                    sortedSingleQueryElapsed / 5_000_000.0,
                    sortedTypingSequenceElapsed / 5_000_000.0,
                    sortedBroadPrefixElapsed / 5_000_000.0);
        }
    }

    private static void scanWhileTyping(IndexedSearchEngine searchEngine) {
        for (String prefix : PREFIXES) {
            searchEngine.suggest(prefix, 10);
        }
    }

    private static void sortedWhileTyping(SortedVocabulary vocabulary) {
        for (String prefix : PREFIXES) {
            vocabulary.suggest(prefix, 10);
        }
    }
}
