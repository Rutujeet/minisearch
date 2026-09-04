package minisearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
        Map<Integer, Integer> matchCounts = new HashMap<>();
        Set<String> queryTerms = new HashSet<>(preprocessor.tokenize(query));

        for (String term : queryTerms) {
            List<Integer> documentIds = termToDocumentIds.get(term);
            if (documentIds == null) {
                continue;
            }

            for (Integer documentId : documentIds) {
                matchCounts.merge(documentId, 1, Integer::sum);
            }
        }

        List<Integer> documentIds = new ArrayList<>(matchCounts.keySet());
        documentIds.sort(Comparator.<Integer, Integer>comparing(matchCounts::get)
                .reversed()
                .thenComparing(Integer::intValue));

        List<Document> results = new ArrayList<>();
        for (Integer documentId : documentIds) {
            results.add(documentsById.get(documentId));
        }
        return results;
    }
}
