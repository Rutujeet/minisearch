package minisearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IndexSnapshotMergerTest {
    private final IndexSnapshotMerger merger = new IndexSnapshotMerger();

    @Test
    void mergesSortedPostingsAndPreservesStoredMetadata() {
        IndexSnapshot first = new IndexSnapshot(
                List.of(new StoredDocument(new Document(1, "One", "java"), 2)),
                Map.of("java", List.of(new Posting(1, 2, List.of(0, 5)))),
                2);
        IndexSnapshot second = new IndexSnapshot(
                List.of(new StoredDocument(new Document(10, "Ten", "java"), 3)),
                Map.of("java", List.of(new Posting(10, 3, List.of(2, 4, 9)))),
                3);

        IndexSnapshot merged = merger.merge(List.of(first, second));

        assertEquals(List.of(1, 10), merged.documents().stream()
                .map(stored -> stored.document().id())
                .toList());
        assertEquals(5, merged.totalDocumentLength());
        assertEquals(List.of(
                new Posting(1, 2, List.of(0, 5)),
                new Posting(10, 3, List.of(2, 4, 9))), merged.postings().get("java"));
    }

    @Test
    void rejectsDuplicateDocumentIds() {
        IndexSnapshot first = new IndexSnapshot(
                List.of(new StoredDocument(new Document(1, "One", "java"), 2)), Map.of(), 2);
        IndexSnapshot second = new IndexSnapshot(
                List.of(new StoredDocument(new Document(1, "Again", "redis"), 2)), Map.of(), 2);

        assertThrows(IllegalArgumentException.class, () -> merger.merge(List.of(first, second)));
    }
}
