package minisearch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
}
