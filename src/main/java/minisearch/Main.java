package minisearch;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<Document> documents = List.of(
                new Document(1, "Redis", "Redis keeps frequently requested data in memory for fast access."),
                new Document(2, "PostgreSQL databases", "PostgreSQL is a relational database with reliable transactions."),
                new Document(3, "Java concurrency", "Java offers threads, executors, and locks for concurrent programs."),
                new Document(4, "Distributed systems", "Distributed systems coordinate machines while handling failures and delay."),
                new Document(5, "Search engines", "A search engine finds relevant documents from a larger collection."),
                new Document(6, "Networking basics", "Networking connects services through protocols, packets, and ports.")
        );

        NaiveSearchEngine searchEngine = new NaiveSearchEngine();
        List<PreparedDocument> preparedDocuments = new DocumentPreprocessor().prepare(documents);
        printResults("redis", searchEngine.search(preparedDocuments, "redis"));
        printResults("java", searchEngine.search(preparedDocuments, "java"));
        printResults("distributed", searchEngine.search(preparedDocuments, "distributed"));
    }

    private static void printResults(String query, List<Document> results) {
        System.out.println("Results for '" + query + "':");
        for (Document document : results) {
            System.out.println(document.id() + " - " + document.title());
        }
    }
}
