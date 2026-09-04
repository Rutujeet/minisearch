package minisearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class IndexedSearchEngine {
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final Comparator<ScoredDocument> BEST_FIRST = Comparator
            .comparingDouble(ScoredDocument::score)
            .reversed()
            .thenComparingInt(ScoredDocument::documentId);

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
            if (postings.size() > 1
                    && postings.get(postings.size() - 2).documentId() > document.id()) {
                // ponytail: sort after an out-of-order append; batch indexing if it becomes costly.
                postings.sort(Comparator.comparingInt(Posting::documentId));
            }
        }
    }

    public List<Document> search(String query) {
        return search(query, Integer.MAX_VALUE);
    }

    public List<Document> search(String query, int limit) {
        if (limit <= 0) {
            return List.of();
        }

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

        List<Integer> documentIds = limit >= scores.size()
                ? rankedDocumentIds(scores)
                : topDocumentIds(scores, limit);

        List<Document> results = new ArrayList<>();
        int resultCount = Math.min(limit, documentIds.size());
        for (int index = 0; index < resultCount; index++) {
            results.add(documentsById.get(documentIds.get(index)));
        }
        return results;
    }

    private List<Integer> rankedDocumentIds(Map<Integer, Double> scores) {
        List<Integer> documentIds = new ArrayList<>(scores.keySet());
        documentIds.sort(Comparator.<Integer, Double>comparing(scores::get)
                .reversed()
                .thenComparing(Integer::intValue));
        return documentIds;
    }

    private List<Integer> topDocumentIds(Map<Integer, Double> scores, int limit) {
        PriorityQueue<ScoredDocument> topK = new PriorityQueue<>(BEST_FIRST.reversed());
        for (Map.Entry<Integer, Double> score : scores.entrySet()) {
            ScoredDocument candidate = new ScoredDocument(score.getKey(), score.getValue());
            if (topK.size() < limit) {
                topK.add(candidate);
            } else if (BEST_FIRST.compare(candidate, topK.peek()) < 0) {
                topK.poll();
                topK.add(candidate);
            }
        }

        List<ScoredDocument> winners = new ArrayList<>(topK);
        winners.sort(BEST_FIRST);
        List<Integer> documentIds = new ArrayList<>();
        for (ScoredDocument winner : winners) {
            documentIds.add(winner.documentId());
        }
        return documentIds;
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

    public List<String> suggest(String prefix, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        String normalizedPrefix = preprocessor.tokenize(prefix).getFirst();
        List<String> suggestions = new ArrayList<>();
        for (String term : termToPostings.keySet()) {
            if (term.startsWith(normalizedPrefix)) {
                suggestions.add(term);
            }
        }
        suggestions.sort(String::compareTo);
        return new ArrayList<>(suggestions.subList(0, Math.min(limit, suggestions.size())));
    }

    SortedVocabulary sortedVocabulary() {
        return new SortedVocabulary(termToPostings.keySet());
    }

    IndexSnapshot snapshot() {
        List<StoredDocument> documents = new ArrayList<>();
        for (Map.Entry<Integer, Document> entry : documentsById.entrySet()) {
            documents.add(new StoredDocument(entry.getValue(), documentLengths.get(entry.getKey())));
        }

        Map<String, List<Posting>> postings = new HashMap<>();
        for (Map.Entry<String, List<Posting>> entry : termToPostings.entrySet()) {
            postings.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return new IndexSnapshot(documents, postings, totalDocumentLength);
    }

    static IndexedSearchEngine fromSnapshot(IndexSnapshot snapshot) {
        IndexedSearchEngine engine = new IndexedSearchEngine();
        for (StoredDocument stored : snapshot.documents()) {
            engine.documentsById.put(stored.document().id(), stored.document());
            engine.documentLengths.put(stored.document().id(), stored.documentLength());
        }
        for (Map.Entry<String, List<Posting>> entry : snapshot.postings().entrySet()) {
            engine.termToPostings.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        engine.totalDocumentLength = snapshot.totalDocumentLength();
        return engine;
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

    private record ScoredDocument(int documentId, double score) {
    }
}
