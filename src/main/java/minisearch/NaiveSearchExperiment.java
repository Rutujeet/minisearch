package minisearch;

import java.util.List;

public class NaiveSearchExperiment {
    private static final String BODY = (
            "Java databases networking distributed systems storage caching transactions replication "
                    + "threads protocols memory indexing queries latency consensus partitions services ").repeat(4);

    public static void main(String[] args) {
        NaiveSearchEngine searchEngine = new NaiveSearchEngine();
        DocumentPreprocessor preprocessor = new DocumentPreprocessor();

        for (int count : List.of(1_000, 10_000, 100_000)) {
            List<Document> documents = generateDocuments(count);
            List<PreparedDocument> preparedDocuments = preprocessor.prepare(documents);

            for (int i = 0; i < 3; i++) {
                searchEngine.search(preparedDocuments, "needle");
            }

            long totalElapsed = 0;
            List<Document> results = List.of();
            for (int i = 0; i < 5; i++) {
                long start = System.nanoTime();
                results = searchEngine.search(preparedDocuments, "needle");
                totalElapsed += System.nanoTime() - start;
            }

            System.out.println("Documents: " + count);
            System.out.println("Matches: " + results.size());
            System.out.printf("Average query time: %.3f ms%n%n", totalElapsed / 5_000_000.0);
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
