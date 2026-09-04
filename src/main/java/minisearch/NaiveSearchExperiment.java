package minisearch;

import java.util.List;

public class NaiveSearchExperiment {
    private static final String BODY = (
            "Java databases networking distributed systems storage caching transactions replication "
                    + "threads protocols memory indexing queries latency consensus partitions services ").repeat(4);

    public static void main(String[] args) {
        NaiveSearchEngine naiveSearchEngine = new NaiveSearchEngine();
        DocumentPreprocessor preprocessor = new DocumentPreprocessor();

        for (int count : List.of(1_000, 10_000, 100_000)) {
            List<Document> documents = generateDocuments(count);
            List<PreparedDocument> preparedDocuments = preprocessor.prepare(documents);
            IndexedSearchEngine indexedSearchEngine = new IndexedSearchEngine();

            long indexStart = System.nanoTime();
            for (Document document : documents) {
                indexedSearchEngine.add(document);
            }
            long indexConstructionTime = System.nanoTime() - indexStart;

            for (int i = 0; i < 3; i++) {
                naiveSearchEngine.search(preparedDocuments, "needle");
                indexedSearchEngine.search("needle");
            }

            long naiveTotalElapsed = 0;
            List<Document> naiveResults = List.of();
            for (int i = 0; i < 5; i++) {
                long start = System.nanoTime();
                naiveResults = naiveSearchEngine.search(preparedDocuments, "needle");
                naiveTotalElapsed += System.nanoTime() - start;
            }

            long indexedTotalElapsed = 0;
            List<Document> indexedResults = List.of();
            for (int i = 0; i < 5; i++) {
                long start = System.nanoTime();
                indexedResults = indexedSearchEngine.search("needle");
                indexedTotalElapsed += System.nanoTime() - start;
            }

            System.out.println("Documents: " + count);
            System.out.printf("Naive prepared scan query: %.3f ms%n", naiveTotalElapsed / 5_000_000.0);
            System.out.printf("Index construction: %.3f ms%n", indexConstructionTime / 1_000_000.0);
            System.out.printf("Indexed query: %.3f ms%n", indexedTotalElapsed / 5_000_000.0);
            System.out.println("Matches: " + indexedResults.size());

            if (naiveResults.size() != indexedResults.size()) {
                throw new IllegalStateException("Search engines returned different match counts");
            }

            System.out.println();
        }
    }

    private static List<Document> generateDocuments(int count) {
        Document[] documents = new Document[count];
        for (int i = 0; i < count; i++) {
            String body = BODY + (i == count - 1 ? "needle" : "");
            documents[i] = new Document(i + 1, "Technical note " + (i + 1), body);
        }
        return List.of(documents);
    }
}
