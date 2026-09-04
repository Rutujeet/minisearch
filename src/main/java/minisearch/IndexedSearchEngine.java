package minisearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IndexedSearchEngine {
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final Map<Integer, Document> documentsById = new HashMap<>();
    private final Map<Integer, Integer> documentLengths = new HashMap<>();
    private final Map<String, List<Posting>> termToPostings = new HashMap<>();
    private final DocumentPreprocessor preprocessor = new DocumentPreprocessor();
    private long totalDocumentLength;

    public void add(Document document) {
        documentsById.put(document.id(), document);

        List<String> terms = preprocessor.prepare(document).terms();
        documentLengths.put(document.id(), terms.size());
        totalDocumentLength += terms.size();

        Map<String, Integer> termCounts = new HashMap<>();
        for (String term : terms) {
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

            for (Posting posting : postings) {
                scores.merge(posting.documentId(), bm25Score(
                        posting.termFrequency(),
                        postings.size(),
                        documentLengths.get(posting.documentId())), Double::sum);
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

    private double bm25Score(int termFrequency, int documentFrequency, int documentLength) {
        double documentCount = documentsById.size();
        double averageDocumentLength = (double) totalDocumentLength / documentCount;
        double idf = Math.log(1 + (documentCount - documentFrequency + 0.5)
                / (documentFrequency + 0.5));
        double lengthNormalization = 1 - B + B * documentLength / averageDocumentLength;
        return idf * termFrequency * (K1 + 1) / (termFrequency + K1 * lengthNormalization);
    }
}
