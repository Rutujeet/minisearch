package minisearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NaiveSearchEngine {
    public List<Document> search(List<PreparedDocument> documents, String query) {
        List<Document> results = new ArrayList<>();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);

        for (PreparedDocument document : documents) {
            for (String term : document.terms()) {
                if (term.equals(normalizedQuery)) {
                    results.add(document.document());
                    break;
                }
            }
        }

        return results;
    }
}
