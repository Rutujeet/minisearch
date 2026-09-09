package minisearch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentedSearchEngineTest {
    @TempDir
    Path tempDir;

    @Test
    void searchesImmutableSegmentsAndMutableDocuments() throws IOException {
        SegmentedSearchEngine searchEngine = new SegmentedSearchEngine(tempDir);
        Document first = new Document(1, "First", "distributed systems java concurrency");
        Document second = new Document(2, "Second", "distributed systems java spring distribution");
        Document third = new Document(3, "Third", "distributed systems java concurrency");
        searchEngine.add(first);
        searchEngine.flush();
        searchEngine.add(second);
        searchEngine.flush();
        searchEngine.add(third);

        assertEquals(List.of(first, second, third), searchEngine.search("distributed"));
        assertEquals(List.of(first, second, third), searchEngine.searchPhrase("distributed systems"));
        assertEquals(List.of(first, third), searchEngine.searchAnd("java", "concurrency"));
        assertEquals(List.of(first, third), searchEngine.searchAndNot("java", "spring"));
        assertEquals(List.of("distributed", "distribution"), searchEngine.suggest("distr", 10));

        searchEngine.flush();
        SegmentedSearchEngine restarted = new SegmentedSearchEngine(tempDir);
        assertEquals(List.of(first, second, third), restarted.search("distributed"));
        assertEquals(List.of(first, second, third), restarted.searchPhrase("distributed systems"));
    }

    @Test
    void laterFlushDoesNotChangeAnOlderSegmentFile() throws IOException {
        SegmentedSearchEngine searchEngine = new SegmentedSearchEngine(tempDir);
        searchEngine.add(new Document(1, "First", "java"));
        searchEngine.flush();
        Path firstSegment = searchEngine.segmentPaths().getFirst();
        byte[] firstSegmentBytes = Files.readAllBytes(firstSegment);

        searchEngine.add(new Document(2, "Second", "java"));
        searchEngine.flush();

        assertArrayEquals(firstSegmentBytes, Files.readAllBytes(firstSegment));
    }

    @Test
    void mergeAllSegmentsPreservesDocumentsAndReplacesOldFiles() throws IOException {
        SegmentedSearchEngine searchEngine = new SegmentedSearchEngine(tempDir);
        Document first = new Document(1, "First", "distributed distributed systems java concurrency");
        Document second = new Document(2, "Second", "redis distribution java spring");
        Document third = new Document(3, "Third", "distributed systems java concurrency");
        searchEngine.add(first);
        searchEngine.flush();
        searchEngine.add(second);
        searchEngine.flush();
        searchEngine.add(third);
        List<Path> oldSegments = searchEngine.segmentPaths();

        searchEngine.mergeAllSegments();

        assertEquals(Set.of(1, 2, 3), searchEngine.search("java").stream()
                .map(Document::id)
                .collect(Collectors.toSet()));
        assertEquals(List.of(first, third), searchEngine.searchPhrase("distributed systems"));
        assertEquals(List.of(first, third), searchEngine.searchAnd("java", "concurrency"));
        assertEquals(List.of(first, third), searchEngine.searchAndNot("java", "spring"));
        assertEquals(List.of("distributed", "distribution"), searchEngine.suggest("distr", 10));
        assertEquals(1, searchEngine.segmentPaths().size());
        assertTrue(Files.exists(searchEngine.segmentPaths().getFirst()));
        for (Path oldSegment : oldSegments) {
            assertFalse(Files.exists(oldSegment));
        }

        searchEngine.flush();
        SegmentedSearchEngine restarted = new SegmentedSearchEngine(tempDir);
        assertEquals(List.of(first, second, third), restarted.search("java"));
    }

    @Test
    void structuralMergeMatchesAnIndexBuiltFromTheSameDocuments() throws IOException {
        Document first = new Document(1, "Java Java", "distributed systems java concurrency");
        Document second = new Document(10, "Redis", "distributed systems java spring");
        IndexedSearchEngine control = new IndexedSearchEngine();
        control.add(first);
        control.add(second);

        SegmentedSearchEngine searchEngine = new SegmentedSearchEngine(tempDir);
        searchEngine.add(first);
        searchEngine.flush();
        searchEngine.add(second);
        searchEngine.flush();
        searchEngine.mergeAllSegments();

        assertEquals(control.search("java"), searchEngine.search("java"));
        assertEquals(control.searchPhrase("distributed systems"), searchEngine.searchPhrase("distributed systems"));
        assertEquals(control.searchAnd("java", "concurrency"), searchEngine.searchAnd("java", "concurrency"));
        assertEquals(control.suggest("distr", 10), searchEngine.suggest("distr", 10));
    }

    @Test
    void deleteHidesPersistedDocumentsFromAllQueryTypesAndSurvivesRestart() throws IOException {
        Document deleted = new Document(1, "Deleted", "legacy phrase java spring obsoleteword");
        Document retained = new Document(2, "Retained", "active phrase java concurrency");
        SegmentedSearchEngine searchEngine = new SegmentedSearchEngine(tempDir);
        searchEngine.add(deleted);
        searchEngine.flush();
        searchEngine.add(retained);
        searchEngine.flush();

        searchEngine.delete(deleted.id());

        assertEquals(List.of(), searchEngine.search("legacy"));
        assertEquals(List.of(), searchEngine.searchPhrase("legacy phrase"));
        assertEquals(List.of(), searchEngine.searchAnd("java", "spring"));
        assertEquals(List.of(retained), searchEngine.searchAndNot("java", "spring"));
        assertEquals(List.of(), searchEngine.suggest("obsolete", 10));
        assertEquals(List.of("java"), searchEngine.suggest("ja", 10));

        SegmentedSearchEngine restarted = new SegmentedSearchEngine(tempDir);
        assertEquals(List.of(), restarted.search("legacy"));
        assertEquals(List.of(retained), restarted.search("active"));
    }

    @Test
    void updateHidesOldContentAndMakesNewContentSearchable() throws IOException {
        SegmentedSearchEngine searchEngine = new SegmentedSearchEngine(tempDir);
        searchEngine.add(new Document(42, "Old", "legacy java"));
        searchEngine.flush();

        Document updated = new Document(42, "New", "modern redis");
        searchEngine.update(updated);

        assertEquals(List.of(), searchEngine.search("legacy"));
        assertEquals(List.of(updated), searchEngine.search("modern"));
        searchEngine.flush();
        SegmentedSearchEngine restarted = new SegmentedSearchEngine(tempDir);
        assertEquals(List.of(), restarted.search("legacy"));
        assertEquals(List.of(updated), restarted.search("modern"));
    }

    @Test
    void mergePhysicallyRemovesDeletedDocumentAndClearsItsTombstone() throws IOException {
        SegmentedSearchEngine searchEngine = new SegmentedSearchEngine(tempDir);
        searchEngine.add(new Document(7, "Old", "legacy content"));
        searchEngine.flush();
        searchEngine.delete(7);

        searchEngine.mergeAllSegments();
        Document replacement = new Document(7, "New", "modern content");
        searchEngine.add(replacement);
        searchEngine.flush();

        SegmentedSearchEngine restarted = new SegmentedSearchEngine(tempDir);
        assertEquals(List.of(), restarted.search("legacy"));
        assertEquals(List.of(replacement), restarted.search("modern"));
    }
}
