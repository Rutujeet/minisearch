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
        Document third = new Document(3, "Concurrency patterns", "Distributed systems today.");
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

    @Test
    void ranksDocumentsHigherWhenQueryTermsOccurMoreOftenAtSimilarLengths() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document first = new Document(1, "java concurrency guide patterns examples details", "");
        Document second = new Document(2, "java java java java java concurrency", "");
        Document third = new Document(3, "java guide patterns examples details more", "");
        Document fourth = new Document(4, "python guide patterns examples details more", "");
        searchEngine.add(first);
        searchEngine.add(second);
        searchEngine.add(third);
        searchEngine.add(fourth);

        assertEquals(List.of(second, first, third), searchEngine.search("java concurrency"));
    }

    @Test
    void commonTermsCanOverpowerMoreInformativeTerms() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document first = new Document(1, "java java java java java programming language tutorial", "");
        Document second = new Document(2, "java distributed systems", "");
        Document third = new Document(3, "java databases", "");
        Document fourth = new Document(4, "java networking", "");
        Document fifth = new Document(5, "java concurrency", "");
        searchEngine.add(first);
        searchEngine.add(second);
        searchEngine.add(third);
        searchEngine.add(fourth);
        searchEngine.add(fifth);

        assertEquals(List.of(second, first, third, fourth, fifth), searchEngine.search("java distributed"));
    }

    @Test
    void focusedDocumentShouldBeatLongDocumentWithRepeatedTerms() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document focused = new Document(1, "Focused", "distributed systems architecture");
        Document broad = new Document(2, "Broad", "distributed ".repeat(10)
                + "systems systems java databases networking caching storage threads protocols "
                + "transactions replication queues operating kernel ".repeat(15));
        Document third = new Document(3, "Java", "databases and transactions");
        Document fourth = new Document(4, "Networking", "protocols and sockets");
        Document fifth = new Document(5, "Caching", "memory caching and storage");
        searchEngine.add(focused);
        searchEngine.add(broad);
        searchEngine.add(third);
        searchEngine.add(fourth);
        searchEngine.add(fifth);

        assertEquals(List.of(focused, broad), searchEngine.search("distributed systems"));
    }

    @Test
    void phraseSearchRequiresTermsToBeAdjacentAndInOrder() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document exact = new Document(1, "Exact", "distributed systems handle failures");
        Document separated = new Document(2, "Separated", "distributed databases connect many systems");
        searchEngine.add(exact);
        searchEngine.add(separated);

        assertEquals(List.of(exact), searchEngine.searchPhrase("distributed systems"));
    }

    @Test
    void phraseSearchRejectsTermsInTheWrongOrder() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        searchEngine.add(new Document(1, "Wrong order", "systems distributed across regions"));

        assertEquals(List.of(), searchEngine.searchPhrase("distributed systems"));
    }

    @Test
    void phraseSearchReturnsARepeatedPhraseOnlyOnce() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document document = new Document(1, "Repeated", "distributed systems and distributed systems");
        searchEngine.add(document);

        assertEquals(List.of(document), searchEngine.searchPhrase("distributed systems"));
    }

    @Test
    void phraseSearchRequiresAllThreeTermsToBeAdjacent() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document exact = new Document(1, "Exact", "large distributed systems are useful");
        Document separated = new Document(2, "Separated", "large distributed reliable systems");
        searchEngine.add(exact);
        searchEngine.add(separated);

        assertEquals(List.of(exact), searchEngine.searchPhrase("large distributed systems"));
    }

    @Test
    void phraseSearchDoesNotCrossTheTitleAndBodyBoundary() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        searchEngine.add(new Document(1, "Distributed", "Systems are difficult"));

        assertEquals(List.of(), searchEngine.searchPhrase("distributed systems"));
    }

    @Test
    void andSearchRequiresBothTerms() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document first = new Document(1, "java", "");
        Document second = new Document(2, "java concurrency", "");
        Document third = new Document(4, "java concurrency", "");
        Document fourth = new Document(5, "concurrency", "");
        searchEngine.add(first);
        searchEngine.add(second);
        searchEngine.add(third);
        searchEngine.add(fourth);

        assertEquals(List.of(second, third), searchEngine.searchAnd("java", "concurrency"));
    }

    @Test
    void orSearchReturnsTheUnionWithoutDuplicates() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document first = new Document(1, "java", "");
        Document second = new Document(2, "java concurrency", "");
        Document third = new Document(4, "java concurrency", "");
        Document fourth = new Document(5, "concurrency", "");
        searchEngine.add(first);
        searchEngine.add(second);
        searchEngine.add(third);
        searchEngine.add(fourth);

        assertEquals(List.of(first, second, third, fourth), searchEngine.searchOr("java", "concurrency"));
    }

    @Test
    void andNotSearchExcludesDocumentsContainingTheSecondTerm() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document first = new Document(1, "java", "");
        Document second = new Document(2, "java spring", "");
        Document third = new Document(4, "java", "");
        searchEngine.add(first);
        searchEngine.add(second);
        searchEngine.add(third);

        assertEquals(List.of(first, third), searchEngine.searchAndNot("java", "spring"));
    }

    @Test
    void booleanSearchHandlesUnknownTerms() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document document = new Document(1, "java", "");
        searchEngine.add(document);

        assertEquals(List.of(), searchEngine.searchAnd("java", "unknown"));
        assertEquals(List.of(document), searchEngine.searchOr("java", "unknown"));
        assertEquals(List.of(document), searchEngine.searchAndNot("java", "unknown"));
    }

    @Test
    void booleanSearchReturnsResultsInAscendingDocumentIdOrder() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document first = new Document(20, "java", "");
        Document second = new Document(3, "java", "");
        Document third = new Document(11, "java", "");
        searchEngine.add(first);
        searchEngine.add(second);
        searchEngine.add(third);

        assertEquals(List.of(second, third, first), searchEngine.searchOr("java", "unknown"));
    }

    @Test
    void searchLimitReturnsOnlyTheHighestRankedDocuments() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document first = new Document(1, "java a b c d e f g h i", "");
        Document second = new Document(2, "java java a b c d e f g h", "");
        Document third = new Document(3, "java java java a b c d e f g", "");
        Document fourth = new Document(4, "java java java java a b c d e f", "");
        Document fifth = new Document(5, "java java java java java a b c d e", "");
        searchEngine.add(first);
        searchEngine.add(second);
        searchEngine.add(third);
        searchEngine.add(fourth);
        searchEngine.add(fifth);

        assertEquals(List.of(fifth, fourth, third), searchEngine.search("java", 3));
        assertEquals(List.of(fifth, fourth, third, second, first), searchEngine.search("java", 10));
        assertEquals(List.of(), searchEngine.search("java", 0));
    }

    @Test
    void searchLimitPrefersLowerDocumentIdsAtTheBoundaryOfATie() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document first = new Document(8, "java", "");
        Document second = new Document(3, "java", "");
        Document third = new Document(20, "java", "");
        Document fourth = new Document(1, "java", "");
        Document fifth = new Document(7, "java", "");
        searchEngine.add(first);
        searchEngine.add(second);
        searchEngine.add(third);
        searchEngine.add(fourth);
        searchEngine.add(fifth);

        assertEquals(List.of(fourth, second, fifth), searchEngine.search("java", 3));
    }

    @Test
    void unlimitedAndVeryLargeLimitsHaveTheSameOrdering() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        searchEngine.add(new Document(1, "java", ""));
        searchEngine.add(new Document(2, "java java", ""));
        searchEngine.add(new Document(3, "java java java", ""));

        assertEquals(searchEngine.search("java"), searchEngine.search("java", 1_000));
    }

    @Test
    void doesNotReturnTheSameDocumentTwiceForRepeatedQueryTerms() {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document document = new Document(1, "java", "");
        searchEngine.add(document);

        assertEquals(List.of(document), searchEngine.search("java java"));
    }
}
