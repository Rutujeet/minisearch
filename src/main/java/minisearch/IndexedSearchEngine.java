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
    private final Map<String, List<Posting>> termToPostings = new HashMap<>();
    private final DocumentPreprocessor preprocessor = new DocumentPreprocessor();

    public void add(Document document) {
        documentsById.put(document.id(), document);

        Map<String, Integer> termCounts = new HashMap<>();
        for (String term : preprocessor.prepare(document).terms()) {
            termCounts.merge(term, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> termCount : termCounts.entrySet()) {
            termToPostings.computeIfAbsent(termCount.getKey(), ignored -> new ArrayList<>())
                    .add(new Posting(document.id(), termCount.getValue()));
        }
    }

    public List<Document> search(String query) {
        Map<Integer, Double> scores = new HashMap<>();
        Set<String> queryTerms = new HashSet<>(preprocessor.tokenize(query));

        for (String term : queryTerms) {
            List<Posting> postings = termToPostings.get(term);
            if (postings == null) {
                continue;
            }

            double idf = Math.log((double) documentsById.size() / postings.size());
            for (Posting posting : postings) {
                scores.merge(posting.documentId(), posting.termFrequency() * idf, Double::sum);
            }
        }

        List<Integer> documentIds = new ArrayList<>(scores.keySet());
        documentIds.sort(Comparator.<Integer, Double>comparing(scores::get)
                .reversed()
                .thenComparing(Integer::intValue));

        List<Document> results = new ArrayList<>();
        for (Integer documentId : documentIds) {
            results.add(documentsById.get(documentId));
        }
        return results;
    }
}
