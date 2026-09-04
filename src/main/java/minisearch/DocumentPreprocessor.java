package minisearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DocumentPreprocessor {
    public PreparedDocument prepare(Document document) {
        List<String> terms = new ArrayList<>();
        terms.addAll(tokenize(document.title()));
        terms.addAll(tokenize(document.body()));
        return new PreparedDocument(document, terms);
    }

    public List<PreparedDocument> prepare(List<Document> documents) {
        List<PreparedDocument> preparedDocuments = new ArrayList<>();
        for (Document document : documents) {
            preparedDocuments.add(prepare(document));
        }
        return preparedDocuments;
    }

    public List<String> tokenize(String text) {
        List<String> terms = new ArrayList<>();
        for (String term : text.toLowerCase(Locale.ROOT).split("\\s+")) {
            terms.add(term);
        }
        return terms;
    }
}
