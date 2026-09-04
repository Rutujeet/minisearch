package minisearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DocumentPreprocessor {
    public PreparedDocument prepare(Document document) {
        List<String> terms = new ArrayList<>();
        addTerms(document.title(), terms);
        addTerms(document.body(), terms);
        return new PreparedDocument(document, terms);
    }

    public List<PreparedDocument> prepare(List<Document> documents) {
        List<PreparedDocument> preparedDocuments = new ArrayList<>();
        for (Document document : documents) {
            preparedDocuments.add(prepare(document));
        }
        return preparedDocuments;
    }

    private void addTerms(String text, List<String> terms) {
        for (String term : text.toLowerCase(Locale.ROOT).split("\\s+")) {
            terms.add(term);
        }
    }
}
