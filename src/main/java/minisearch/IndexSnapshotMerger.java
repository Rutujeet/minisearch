package minisearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Combines already-indexed snapshots without tokenizing their documents again. */
final class IndexSnapshotMerger {
    IndexSnapshot merge(List<IndexSnapshot> snapshots) {
        Map<Integer, StoredDocument> documentsById = new HashMap<>();
        Map<String, List<Posting>> postingsByTerm = new HashMap<>();
        long totalDocumentLength = 0;

        for (IndexSnapshot snapshot : snapshots) {
            for (StoredDocument document : snapshot.documents()) {
                if (documentsById.putIfAbsent(document.document().id(), document) != null) {
                    throw new IllegalArgumentException("Duplicate document ID across segments: "
                            + document.document().id());
                }
            }
            totalDocumentLength += snapshot.totalDocumentLength();
            for (Map.Entry<String, List<Posting>> entry : snapshot.postings().entrySet()) {
                postingsByTerm.merge(entry.getKey(), new ArrayList<>(entry.getValue()), this::mergePostings);
            }
        }

        List<StoredDocument> documents = new ArrayList<>(documentsById.values());
        documents.sort(Comparator.comparingInt(document -> document.document().id()));
        return new IndexSnapshot(documents, postingsByTerm, totalDocumentLength);
    }

    IndexSnapshot withoutDocuments(IndexSnapshot snapshot, Set<Integer> documentIds) {
        if (documentIds.isEmpty()) {
            return snapshot;
        }

        List<StoredDocument> documents = new ArrayList<>();
        long totalDocumentLength = 0;
        for (StoredDocument document : snapshot.documents()) {
            if (!documentIds.contains(document.document().id())) {
                documents.add(document);
                totalDocumentLength += document.documentLength();
            }
        }

        Map<String, List<Posting>> postings = new HashMap<>();
        for (Map.Entry<String, List<Posting>> entry : snapshot.postings().entrySet()) {
            List<Posting> livePostings = entry.getValue().stream()
                    .filter(posting -> !documentIds.contains(posting.documentId()))
                    .toList();
            if (!livePostings.isEmpty()) {
                postings.put(entry.getKey(), livePostings);
            }
        }
        return new IndexSnapshot(documents, postings, totalDocumentLength);
    }

    private List<Posting> mergePostings(List<Posting> first, List<Posting> second) {
        List<Posting> merged = new ArrayList<>(first.size() + second.size());
        int firstIndex = 0;
        int secondIndex = 0;
        while (firstIndex < first.size() && secondIndex < second.size()) {
            Posting firstPosting = first.get(firstIndex);
            Posting secondPosting = second.get(secondIndex);
            if (firstPosting.documentId() < secondPosting.documentId()) {
                merged.add(firstPosting);
                firstIndex++;
            } else if (firstPosting.documentId() > secondPosting.documentId()) {
                merged.add(secondPosting);
                secondIndex++;
            } else {
                throw new IllegalArgumentException("Duplicate posting document ID: " + firstPosting.documentId());
            }
        }
        merged.addAll(first.subList(firstIndex, first.size()));
        merged.addAll(second.subList(secondIndex, second.size()));
        return merged;
    }
}
