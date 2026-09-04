package minisearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class IndexedSearchEngineTest {
    @Test
    void findsTermInTitle() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document document = new Document(1, "Redis guide", "An in-memory data store.");
        searchEngine.add(document);

        assertEquals(List.of(document), searchEngine.search("redis"));
    }

    @Test
    void findsTermInBody() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document document = new Document(1, "Caching guide", "Redis keeps data in memory.");
        searchEngine.add(document);

        assertEquals(List.of(document), searchEngine.search("redis"));
    }

    @Test
    void ignoresQueryCase() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document document = new Document(1, "Redis guide", "An in-memory data store.");
        searchEngine.add(document);

        assertEquals(List.of(document), searchEngine.search("ReDiS"));
    }

    @Test
    void returnsNoDocumentsForAnAbsentTerm() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        searchEngine.add(new Document(1, "Redis guide", "An in-memory data store."));

        assertEquals(List.of(), searchEngine.search("networking"));
    }

    @Test
    void returnsEveryDocumentContainingTheTerm() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document first = new Document(1, "Java guide", "Language basics.");
        Document second = new Document(2, "Concurrency", "Java supports threads.");
        searchEngine.add(first);
        searchEngine.add(second);

        assertEquals(List.of(first, second), searchEngine.search("java"));
    }

    @Test
    void doesNotReturnTheSameDocumentMoreThanOnceForRepeatedTerms() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document document = new Document(1, "Redis", "Redis redis redis is fast.");
        searchEngine.add(document);

        assertEquals(List.of(document), searchEngine.search("redis"));
    }

    @Test
    void ranksDocumentsByTheNumberOfQueryTermsTheyMatch() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document first = new Document(1, "Java concurrency", "Threads and executors.");
        Document second = new Document(2, "Java collections", "Lists and streams.");
        Document third = new Document(3, "Concurrency patterns", "Distributed systems.");
        searchEngine.add(first);
        searchEngine.add(second);
        searchEngine.add(third);

        assertEquals(List.of(first, second, third), searchEngine.search("java concurrency"));
    }

    @Test
    void keepsSingleTermSearchBehavior() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document first = new Document(1, "Java concurrency", "Threads and executors.");
        Document second = new Document(2, "Java collections", "Lists and streams.");
        Document third = new Document(3, "Concurrency patterns", "Distributed systems.");
        searchEngine.add(first);
        searchEngine.add(second);
        searchEngine.add(third);

        assertEquals(List.of(first, second), searchEngine.search("java"));
    }

    @Test
    void returnsNothingWhenAllQueryTermsAreUnknown() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        searchEngine.add(new Document(1, "Java concurrency", "Threads and executors."));

        assertEquals(List.of(), searchEngine.search("golang kubernetes"));
    }
}
