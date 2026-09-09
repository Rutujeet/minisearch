package minisearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

/** Searches immutable persisted segments and a mutable in-memory index. */
public class SegmentedSearchEngine {
    private final Path segmentsDirectory;
    private final SegmentStorage segmentStorage = new SegmentStorage();
    private final IndexSnapshotMerger snapshotMerger = new IndexSnapshotMerger();
    private final TombstoneStorage tombstoneStorage = new TombstoneStorage();
    private final List<Segment> segments = new ArrayList<>();
    private final Map<Integer, Integer> deletedThroughSegment;
    private final DocumentPreprocessor preprocessor = new DocumentPreprocessor();
    private IndexedSearchEngine mutableIndex = new IndexedSearchEngine();
    private int nextSegmentNumber = 1;

    /** Opens the segments in {@code segmentsDirectory}, creating the directory when needed. */
    public SegmentedSearchEngine(Path segmentsDirectory) throws IOException {
        this.segmentsDirectory = segmentsDirectory;
        Files.createDirectories(segmentsDirectory);
        deletedThroughSegment = tombstoneStorage.load(tombstonePath());
        try (Stream<Path> paths = Files.list(segmentsDirectory)) {
            for (Path path : paths
                    .filter(path -> path.getFileName().toString().matches("segment-\\d+\\.bin"))
                    .sorted()
                    .toList()) {
                segments.add(segmentStorage.load(path));
                nextSegmentNumber = Math.max(nextSegmentNumber, segmentNumber(path) + 1);
            }
        }
    }

    /** Adds a document to the current mutable index. */
    public void add(Document document) {
        mutableIndex.add(document);
    }

    /** Hides a document from persisted segments and removes a mutable copy. */
    public void delete(int documentId) throws IOException {
        deletedThroughSegment.merge(documentId, latestSegmentNumber(), Math::max);
        mutableIndex.remove(documentId);
        tombstoneStorage.save(deletedThroughSegment, tombstonePath());
    }

    /** Replaces a document by tombstoning its old persisted copy and indexing a new mutable copy. */
    public void update(Document document) throws IOException {
        delete(document.id());
        mutableIndex.add(document);
    }

    /** Persists the current mutable index as a new immutable segment. */
    public void flush() throws IOException {
        if (mutableIndex.documentCount() == 0) {
            return;
        }

        Path path = nextSegmentPath();
        segments.add(segmentStorage.write(mutableIndex, path));
        mutableIndex = new IndexedSearchEngine();
    }

    /** Rebuilds all immutable segments into one new immutable segment. */
    public MergeTimings mergeAllSegments() throws IOException {
        if (segments.isEmpty() || (segments.size() < 2 && deletedThroughSegment.isEmpty())) {
            return MergeTimings.empty();
        }

        long start = System.nanoTime();
        List<IndexSnapshot> snapshots = new ArrayList<>();
        for (Segment segment : segments) {
            snapshots.add(snapshotMerger.withoutDocuments(
                    segment.index().snapshot(), deletedIn(segment)));
        }
        IndexedSearchEngine mergedIndex = IndexedSearchEngine.fromSnapshot(snapshotMerger.merge(snapshots));
        long structuralMergeNanos = System.nanoTime() - start;

        start = System.nanoTime();
        Segment mergedSegment = segmentStorage.write(mergedIndex, nextSegmentPath());
        long writeNanos = System.nanoTime() - start;

        start = System.nanoTime();
        List<Segment> oldSegments = new ArrayList<>(segments);
        segments.clear();
        segments.add(mergedSegment);
        for (Segment oldSegment : oldSegments) {
            Files.delete(oldSegment.path());
        }
        deletedThroughSegment.clear();
        tombstoneStorage.save(deletedThroughSegment, tombstonePath());
        long cleanupNanos = System.nanoTime() - start;
        return new MergeTimings(structuralMergeNanos, writeNanos, cleanupNanos);
    }

    public List<Document> search(String query) {
        return search(query, Integer.MAX_VALUE);
    }

    public List<Document> search(String query, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        // ponytail: document-ID order across segments; aggregate BM25 statistics when relevance requires it.
        List<Document> results = acrossIndexes(index -> index.search(query));
        return new ArrayList<>(results.subList(0, Math.min(limit, results.size())));
    }

    public List<Document> searchPhrase(String phrase) {
        return acrossIndexes(index -> index.searchPhrase(phrase));
    }

    public List<Document> searchAnd(String firstTerm, String secondTerm) {
        return acrossIndexes(index -> index.searchAnd(firstTerm, secondTerm));
    }

    public List<Document> searchOr(String firstTerm, String secondTerm) {
        return acrossIndexes(index -> index.searchOr(firstTerm, secondTerm));
    }

    public List<Document> searchAndNot(String requiredTerm, String excludedTerm) {
        return acrossIndexes(index -> index.searchAndNot(requiredTerm, excludedTerm));
    }

    public List<String> suggest(String prefix, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        String normalizedPrefix = preprocessor.tokenize(prefix).getFirst();
        Set<String> vocabulary = new TreeSet<>();
        for (IndexedSearchEngine index : indexes()) {
            vocabulary.addAll(index.vocabulary());
        }

        List<String> suggestions = new ArrayList<>();
        for (String term : vocabulary) {
            if (term.startsWith(normalizedPrefix) && containsLiveTerm(term)) {
                suggestions.add(term);
                if (suggestions.size() == limit) {
                    break;
                }
            }
        }
        return suggestions;
    }

    List<Path> segmentPaths() {
        List<Path> paths = new ArrayList<>();
        for (Segment segment : segments) {
            paths.add(segment.path());
        }
        return List.copyOf(paths);
    }

    private List<Document> acrossIndexes(Function<IndexedSearchEngine, List<Document>> search) {
        TreeMap<Integer, Document> documents = new TreeMap<>();
        for (Segment segment : segments) {
            for (Document document : search.apply(segment.index())) {
                if (!isDeletedIn(segment, document.id())) {
                    documents.put(document.id(), document);
                }
            }
        }
        for (Document document : search.apply(mutableIndex)) {
            documents.put(document.id(), document);
        }
        return new ArrayList<>(documents.values());
    }

    private boolean containsLiveTerm(String term) {
        for (Segment segment : segments) {
            List<Posting> postings = segment.index().snapshot().postings().get(term);
            if (postings != null && postings.stream().anyMatch(posting -> !isDeletedIn(segment, posting.documentId()))) {
                return true;
            }
        }
        return mutableIndex.snapshot().postings().containsKey(term);
    }

    private Set<Integer> deletedIn(Segment segment) {
        Set<Integer> documentIds = new TreeSet<>();
        for (Map.Entry<Integer, Integer> tombstone : deletedThroughSegment.entrySet()) {
            if (tombstone.getValue() >= segmentNumber(segment.path())) {
                documentIds.add(tombstone.getKey());
            }
        }
        return documentIds;
    }

    private boolean isDeletedIn(Segment segment, int documentId) {
        return deletedThroughSegment.getOrDefault(documentId, -1) >= segmentNumber(segment.path());
    }

    private int latestSegmentNumber() {
        return nextSegmentNumber - 1;
    }

    private Path tombstonePath() {
        return segmentsDirectory.resolve("tombstones.bin");
    }

    private List<IndexedSearchEngine> indexes() {
        List<IndexedSearchEngine> indexes = new ArrayList<>();
        for (Segment segment : segments) {
            indexes.add(segment.index());
        }
        indexes.add(mutableIndex);
        return indexes;
    }

    private Path nextSegmentPath() {
        return segmentsDirectory.resolve("segment-%06d.bin".formatted(nextSegmentNumber++));
    }

    private int segmentNumber(Path path) {
        String fileName = path.getFileName().toString();
        return Integer.parseInt(fileName.substring("segment-".length(), fileName.length() - ".bin".length()));
    }

    /** Timings for the coarse phases of one manual merge. */
    public record MergeTimings(long structuralMergeNanos, long writeNanos, long cleanupNanos) {
        static MergeTimings empty() {
            return new MergeTimings(0, 0, 0);
        }

        public long totalNanos() {
            return structuralMergeNanos + writeNanos + cleanupNanos;
        }
    }
}
