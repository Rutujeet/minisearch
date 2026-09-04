package minisearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NaiveSearchEngine {
    public List<Document> search(List<Document> documents, String query) {
        List<Document> results = new ArrayList<>();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);

        for (Document document : documents) {
            if (contains(document.title(), normalizedQuery) || contains(document.body(), normalizedQuery)) {
                results.add(document);
            }
        }

        return results;
    }

    private boolean contains(String text, String query) {
        String[] words = text.toLowerCase(Locale.ROOT).split("\\s+");
        for (String word : words) {
            if (word.equals(query)) {
                return true;
            }
        }
        return false;
    }
}
