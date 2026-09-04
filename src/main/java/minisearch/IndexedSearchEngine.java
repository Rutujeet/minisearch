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

        List<String> titleTerms = preprocessor.tokenize(document.title());
        List<String> bodyTerms = preprocessor.tokenize(document.body());
        documentLengths.put(document.id(), titleTerms.size() + bodyTerms.size());
        totalDocumentLength += titleTerms.size() + bodyTerms.size();

        Map<String, List<Integer>> termPositions = new HashMap<>();
        for (int position = 0; position < titleTerms.size(); position++) {
            termPositions.computeIfAbsent(titleTerms.get(position), ignored -> new ArrayList<>()).add(position);
        }
        for (int position = 0; position < bodyTerms.size(); position++) {
            termPositions.computeIfAbsent(bodyTerms.get(position), ignored -> new ArrayList<>())
                    .add(titleTerms.size() + 1 + position);
        }

        for (Map.Entry<String, List<Integer>> termPosition : termPositions.entrySet()) {
            List<Posting> postings = termToPostings.computeIfAbsent(
                    termPosition.getKey(), ignored -> new ArrayList<>());
            postings.add(new Posting(document.id(), termPosition.getValue().size(), termPosition.getValue()));
            // ponytail: sort after each append; batch or insert in order if indexing cost matters.
            postings.sort(Comparator.comparingInt(Posting::documentId));
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

    public List<Document> searchPhrase(String phrase) {
        List<String> phraseTerms = preprocessor.tokenize(phrase);
        Set<Integer> candidates = null;

        for (String term : phraseTerms) {
            List<Posting> postings = termToPostings.get(term);
            if (postings == null) {
                return List.of();
            }

            Set<Integer> documentIds = new HashSet<>();
            for (Posting posting : postings) {
                documentIds.add(posting.documentId());
            }
            if (candidates == null) {
                candidates = documentIds;
            } else {
                candidates.retainAll(documentIds);
            }
        }

        if (candidates == null) {
            return List.of();
        }

        List<Integer> documentIds = new ArrayList<>(candidates);
        documentIds.sort(Integer::compareTo);
        List<Document> results = new ArrayList<>();
        for (Integer documentId : documentIds) {
            if (containsPhrase(documentId, phraseTerms)) {
                results.add(documentsById.get(documentId));
            }
        }
        return results;
    }

    public List<Document> searchAnd(String firstTerm, String secondTerm) {
        return documentsFor(PostingListOperations.intersection(
                postingsFor(firstTerm), postingsFor(secondTerm)));
    }

    public List<Document> searchOr(String firstTerm, String secondTerm) {
        return documentsFor(PostingListOperations.union(
                postingsFor(firstTerm), postingsFor(secondTerm)));
    }

    public List<Document> searchAndNot(String requiredTerm, String excludedTerm) {
        return documentsFor(PostingListOperations.difference(
                postingsFor(requiredTerm), postingsFor(excludedTerm)));
    }

    private boolean containsPhrase(int documentId, List<String> phraseTerms) {
        Posting firstTermPosting = postingFor(documentId, phraseTerms.getFirst());
        for (int startPosition : firstTermPosting.positions()) {
            boolean matches = true;
            for (int termIndex = 1; termIndex < phraseTerms.size(); termIndex++) {
                Posting posting = postingFor(documentId, phraseTerms.get(termIndex));
                if (posting == null || !posting.positions().contains(startPosition + termIndex)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private Posting postingFor(int documentId, String term) {
        for (Posting posting : termToPostings.get(term)) {
            if (posting.documentId() == documentId) {
                return posting;
            }
        }
        return null;
    }

    private List<Posting> postingsFor(String term) {
        return termToPostings.getOrDefault(preprocessor.tokenize(term).getFirst(), List.of());
    }

    private List<Document> documentsFor(List<Integer> documentIds) {
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
