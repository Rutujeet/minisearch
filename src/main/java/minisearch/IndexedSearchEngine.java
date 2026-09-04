package minisearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class IndexedSearchEngine {
    private final Map<Integer, Document> documentsById = new HashMap<>();
    private final Map<String, List<Integer>> termToDocumentIds = new HashMap<>();
    private final DocumentPreprocessor preprocessor = new DocumentPreprocessor();

    public void add(Document document) {
        documentsById.put(document.id(), document);

        Set<String> uniqueTerms = new HashSet<>(preprocessor.prepare(document).terms());
        for (String term : uniqueTerms) {
            termToDocumentIds.computeIfAbsent(term, ignored -> new ArrayList<>()).add(document.id());
        }
    }

    public List<Document> search(String query) {
        List<Document> results = new ArrayList<>();
        List<Integer> documentIds = termToDocumentIds.get(query.toLowerCase(Locale.ROOT));
        if (documentIds == null) {
            return results;
        }

        for (Integer documentId : documentIds) {
            results.add(documentsById.get(documentId));
        }
        return results;
    }
}
