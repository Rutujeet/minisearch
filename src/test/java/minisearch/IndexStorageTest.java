package minisearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadPreserveSearchBehavior() throws IOException {
        IndexedSearchEngine searchEngine = new IndexedSearchEngine();
        Document focused = new Document(1, "Focused", "distributed systems architecture");
        Document broad = new Document(2, "Broad", "distributed ".repeat(10)
                + "systems systems java databases networking caching storage threads protocols "
                + "transactions replication queues operating kernel ".repeat(15));
        Document javaConcurrency = new Document(3, "Java concurrency", "threads and executors");
        Document javaSpring = new Document(4, "Java spring", "framework");
        Document distribution = new Document(5, "Distribution", "redis guide");
        searchEngine.add(focused);
        searchEngine.add(broad);
        searchEngine.add(javaConcurrency);
        searchEngine.add(javaSpring);
        searchEngine.add(distribution);

        List<Document> ranked = searchEngine.search("distributed systems");
        List<Document> phrase = searchEngine.searchPhrase("distributed systems");
        List<Document> and = searchEngine.searchAnd("java", "concurrency");
        List<Document> andNot = searchEngine.searchAndNot("java", "spring");
        List<String> suggestions = searchEngine.suggest("distr", 10);

        Path indexPath = tempDir.resolve("index.bin");
        IndexStorage storage = new IndexStorage();
        storage.save(searchEngine, indexPath);
        IndexedSearchEngine loaded = storage.load(indexPath);

        assertEquals(List.of(focused, broad), ranked);
        assertEquals(ranked, loaded.search("distributed systems"));
        assertEquals(phrase, loaded.searchPhrase("distributed systems"));
        assertEquals(and, loaded.searchAnd("java", "concurrency"));
        assertEquals(andNot, loaded.searchAndNot("java", "spring"));
        assertEquals(suggestions, loaded.suggest("distr", 10));
    }

    @Test
    void loadRejectsInvalidMagicAndVersion() throws IOException {
        IndexStorage storage = new IndexStorage();
        Path invalidMagic = tempDir.resolve("invalid-magic.bin");
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(invalidMagic))) {
            output.writeInt(0);
            output.writeInt(1);
        }

        Path invalidVersion = tempDir.resolve("invalid-version.bin");
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(invalidVersion))) {
            output.writeInt(0x4D534958);
            output.writeInt(2);
        }

        assertThrows(IOException.class, () -> storage.load(invalidMagic));
        assertThrows(IOException.class, () -> storage.load(invalidVersion));
    }
}
