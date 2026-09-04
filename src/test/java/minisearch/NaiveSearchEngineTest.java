package minisearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NaiveSearchEngineTest {
    private final NaiveSearchEngine searchEngine = new NaiveSearchEngine();
    private final DocumentPreprocessor preprocessor = new DocumentPreprocessor();

    @Test
    void returnsDocumentWhenTitleMatches() {
        Document document = new Document(1, "Redis guide", "An in-memory data store.");

        assertEquals(List.of(document), searchEngine.search(List.of(preprocessor.prepare(document)), "redis"));
    }

    @Test
    void returnsDocumentWhenBodyMatches() {
        Document document = new Document(1, "Caching guide", "Redis stores data in memory.");

        assertEquals(List.of(document), searchEngine.search(List.of(preprocessor.prepare(document)), "redis"));
    }

    @Test
    void ignoresCase() {
        Document document = new Document(1, "Redis guide", "An in-memory data store.");

        assertEquals(List.of(document), searchEngine.search(List.of(preprocessor.prepare(document)), "ReDiS"));
    }

    @Test
    void returnsNoDocumentsWhenNothingMatches() {
        Document document = new Document(1, "Redis guide", "An in-memory data store.");

        assertTrue(searchEngine.search(List.of(preprocessor.prepare(document)), "networking").isEmpty());
    }
}
