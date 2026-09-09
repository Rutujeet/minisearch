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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

/** Searches immutable persisted segments and a mutable in-memory index. */
public class SegmentedSearchEngine {
    private final Path segmentsDirectory;
    private final SegmentStorage segmentStorage = new SegmentStorage();
    private final IndexSnapshotMerger snapshotMerger = new IndexSnapshotMerger();
    private final TombstoneStorage tombstoneStorage = new TombstoneStorage();
    private final List<Segment> segments = new ArrayList<>();
    private final Map<Integer, Integer> deletedThroughSegment = new HashMap<>();
    private final DocumentPreprocessor preprocessor = new DocumentPreprocessor();
    // ponytail: serialize writes and copy mutable state; add batching only if write cost becomes a measured problem.
    private final Object writeLock = new Object();
    private final AtomicReference<PublishedState> published = new AtomicReference<>();
    private IndexedSearchEngine mutableIndex = new IndexedSearchEngine();
    private int nextSegmentNumber = 1;

    /** Opens the segments in {@code segmentsDirectory}, creating the directory when needed. */
    public SegmentedSearchEngine(Path segmentsDirectory) throws IOException {
        this.segmentsDirectory = segmentsDirectory;
        Files.createDirectories(segmentsDirectory);
        deletedThroughSegment.putAll(tombstoneStorage.load(tombstonePath()));
        try (Stream<Path> paths = Files.list(segmentsDirectory)) {
            for (Path path : paths
                    .filter(path -> path.getFileName().toString().matches("segment-\\d+\\.bin"))
                    .sorted()
                    .toList()) {
                segments.add(segmentStorage.load(path));
                nextSegmentNumber = Math.max(nextSegmentNumber, segmentNumber(path) + 1);
            }
        }
        publish();
    }

    /** Adds a document to the current mutable index. */
    public void add(Document document) {
        synchronized (writeLock) {
            mutableIndex = IndexedSearchEngine.fromSnapshot(mutableIndex.snapshot());
            mutableIndex.add(document);
            publish();
        }
    }

    /** Hides a document from persisted segments and removes a mutable copy. */
    public void delete(int documentId) throws IOException {
        synchronized (writeLock) {
            deletedThroughSegment.merge(documentId, latestSegmentNumber(), Math::max);
            mutableIndex = IndexedSearchEngine.fromSnapshot(mutableIndex.snapshot());
            mutableIndex.remove(documentId);
            tombstoneStorage.save(deletedThroughSegment, tombstonePath());
            publish();
        }
    }

    /** Replaces a document by tombstoning its old persisted copy and indexing a new mutable copy. */
    public void update(Document document) throws IOException {
        synchronized (writeLock) {
            deletedThroughSegment.merge(document.id(), latestSegmentNumber(), Math::max);
            mutableIndex = IndexedSearchEngine.fromSnapshot(mutableIndex.snapshot());
            mutableIndex.remove(document.id());
            mutableIndex.add(document);
            tombstoneStorage.save(deletedThroughSegment, tombstonePath());
            publish();
        }
    }

    /** Persists the current mutable index as a new immutable segment. */
    public void flush() throws IOException {
        synchronized (writeLock) {
            if (mutableIndex.documentCount() == 0) {
                return;
            }

            Path path = nextSegmentPath();
            segments.add(segmentStorage.write(mutableIndex, path));
            mutableIndex = new IndexedSearchEngine();
            publish();
        }
    }

    /** Rebuilds all immutable segments into one new immutable segment. */
    public MergeTimings mergeAllSegments() throws IOException {
        synchronized (writeLock) {
            if (segments.isEmpty() || (segments.size() < 2 && deletedThroughSegment.isEmpty())) {
                return MergeTimings.empty();
            }

            long start = System.nanoTime();
            List<IndexSnapshot> snapshots = new ArrayList<>();
            for (Segment segment : segments) {
                snapshots.add(snapshotMerger.withoutDocuments(
                        segment.index().snapshot(), deletedIn(segment, deletedThroughSegment)));
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
            publish();
            long cleanupNanos = System.nanoTime() - start;
            return new MergeTimings(structuralMergeNanos, writeNanos, cleanupNanos);
        }
    }

    public List<Document> search(String query) {
        return search(query, Integer.MAX_VALUE);
    }

    public List<Document> search(String query, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        // ponytail: document-ID order across segments; aggregate BM25 statistics when relevance requires it.
        PublishedState state = published.get();
        List<Document> results = acrossIndexes(state, index -> index.search(query));
        return new ArrayList<>(results.subList(0, Math.min(limit, results.size())));
    }

    public List<Document> searchPhrase(String phrase) {
        PublishedState state = published.get();
        return acrossIndexes(state, index -> index.searchPhrase(phrase));
    }

    public List<Document> searchAnd(String firstTerm, String secondTerm) {
        PublishedState state = published.get();
        return acrossIndexes(state, index -> index.searchAnd(firstTerm, secondTerm));
    }

    public List<Document> searchOr(String firstTerm, String secondTerm) {
        PublishedState state = published.get();
        return acrossIndexes(state, index -> index.searchOr(firstTerm, secondTerm));
    }

    public List<Document> searchAndNot(String requiredTerm, String excludedTerm) {
        PublishedState state = published.get();
        return acrossIndexes(state, index -> index.searchAndNot(requiredTerm, excludedTerm));
    }

    public List<String> suggest(String prefix, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        PublishedState state = published.get();
        String normalizedPrefix = preprocessor.tokenize(prefix).getFirst();
        Set<String> vocabulary = new TreeSet<>();
        for (IndexedSearchEngine index : indexes(state)) {
            vocabulary.addAll(index.vocabulary());
        }

        List<String> suggestions = new ArrayList<>();
        for (String term : vocabulary) {
            if (term.startsWith(normalizedPrefix) && containsLiveTerm(state, term)) {
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
        for (Segment segment : published.get().segments()) {
            paths.add(segment.path());
        }
        return List.copyOf(paths);
    }

    private List<Document> acrossIndexes(PublishedState state, Function<IndexedSearchEngine, List<Document>> search) {
        TreeMap<Integer, Document> documents = new TreeMap<>();
        for (Segment segment : state.segments()) {
            for (Document document : search.apply(segment.index())) {
                if (!isDeletedIn(segment, document.id(), state.deletedThroughSegment())) {
                    documents.put(document.id(), document);
                }
            }
        }
        for (Document document : search.apply(state.mutableIndex())) {
            documents.put(document.id(), document);
        }
        return new ArrayList<>(documents.values());
    }

    private boolean containsLiveTerm(PublishedState state, String term) {
        for (Segment segment : state.segments()) {
            List<Posting> postings = segment.index().snapshot().postings().get(term);
            if (postings != null && postings.stream()
                    .anyMatch(posting -> !isDeletedIn(segment, posting.documentId(), state.deletedThroughSegment()))) {
                return true;
            }
        }
        return state.mutableIndex().snapshot().postings().containsKey(term);
    }

    private Set<Integer> deletedIn(Segment segment, Map<Integer, Integer> tombstones) {
        Set<Integer> documentIds = new TreeSet<>();
        for (Map.Entry<Integer, Integer> tombstone : tombstones.entrySet()) {
            if (tombstone.getValue() >= segmentNumber(segment.path())) {
                documentIds.add(tombstone.getKey());
            }
        }
        return documentIds;
    }

    private boolean isDeletedIn(Segment segment, int documentId, Map<Integer, Integer> tombstones) {
        return tombstones.getOrDefault(documentId, -1) >= segmentNumber(segment.path());
    }

    private int latestSegmentNumber() {
        return nextSegmentNumber - 1;
    }

    private Path tombstonePath() {
        return segmentsDirectory.resolve("tombstones.bin");
    }

    private List<IndexedSearchEngine> indexes(PublishedState state) {
        List<IndexedSearchEngine> indexes = new ArrayList<>();
        for (Segment segment : state.segments()) {
            indexes.add(segment.index());
        }
        indexes.add(state.mutableIndex());
        return indexes;
    }

    private void publish() {
        published.set(new PublishedState(List.copyOf(segments), Map.copyOf(deletedThroughSegment), mutableIndex));
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

    private record PublishedState(
            List<Segment> segments,
            Map<Integer, Integer> deletedThroughSegment,
            IndexedSearchEngine mutableIndex) {
    }
}
