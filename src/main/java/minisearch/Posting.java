package minisearch;

import java.util.List;

public record Posting(int documentId, int termFrequency, List<Integer> positions) {
    public Posting {
        positions = List.copyOf(positions);
    }
}
