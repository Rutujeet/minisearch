package minisearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SortedVocabularyTest {
    private final SortedVocabulary vocabulary = new SortedVocabulary(List.of(
            "database", "distance", "distinct", "distributed", "distribution", "docker", "java", "redis"));

    @Test
    void findsTermsStartingAtTheLowerBound() {
        assertEquals(List.of("distributed", "distribution"), vocabulary.suggest("distr", 10));
        assertEquals(List.of("distributed", "distribution"), vocabulary.suggest("DisTr", 10));
    }

    @Test
    void respectsLimitsAndHandlesExactAndMissingPrefixes() {
        assertEquals(List.of("database", "distance", "distinct"), vocabulary.suggest("d", 3));
        assertEquals(List.of("distributed"), vocabulary.suggest("distributed", 10));
        assertEquals(List.of(), vocabulary.suggest("a", 10));
        assertEquals(List.of(), vocabulary.suggest("python", 10));
        assertEquals(List.of(), vocabulary.suggest("zzzz", 10));
    }
}
