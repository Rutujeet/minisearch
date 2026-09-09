# MiniSearch

MiniSearch is a small Java project for learning how a search engine earns each
new piece of structure. It starts with a document scan and grows only when a
measured or tested limitation makes the next idea necessary.

## What it does now

MiniSearch indexes documents with an ID, title, and body. Tokenization is
deliberately basic: lowercase text and split on whitespace.

`IndexedSearchEngine` provides:

- Ranked keyword search with BM25: `search("java concurrency")`
- Ranked top-K search: `search("java concurrency", 10)`
- Exact phrases: `searchPhrase("distributed systems")`
- Simple Boolean operations: `searchAnd`, `searchOr`, and `searchAndNot`
- Case-insensitive prefix suggestions: `suggest("distr", 10)`
- Single-file save and load through `IndexStorage`
- Explicit immutable-segment flushes through `SegmentedSearchEngine`
- Explicit merging of persisted segments through `mergeAllSegments()`
- Updates and deletes through tombstones: `update(document)` and `delete(id)`
- Concurrent queries with query-start snapshot semantics

Keyword queries use OR behavior. Phrase and Boolean queries are separate APIs;
there is no query parser, quotation syntax, or parentheses.

The index stores term frequency and positions. BM25 uses saturated term
frequency, document length normalization, and deterministic ties: higher score
first, then lower document ID.

## Persistence

`IndexStorage` saves a complete loaded-in-memory index to one versioned binary
file and loads it again later. The file contains documents, document lengths,
total indexed token length, and postings with term frequency and positions.

```java
IndexStorage storage = new IndexStorage();
storage.save(searchEngine, Path.of("index.bin"));

IndexedSearchEngine loaded = storage.load(Path.of("index.bin"));
List<Document> results = loaded.search("distributed systems", 10);
```

Loading reconstructs the in-memory structure; queries are not served directly
from disk. Save rewrites the complete file. The snapshot format has no WAL,
background flush, compression, or recovery.

For small incremental writes, `SegmentedSearchEngine` keeps a mutable index
and writes it to a new immutable file when `flush()` is called:

```java
SegmentedSearchEngine segments = new SegmentedSearchEngine(Path.of("segments"));
segments.add(document);
segments.flush();
segments.update(new Document(1, "New title", "new body"));
segments.delete(2);
segments.mergeAllSegments(); // Explicitly merge persisted segments into one.
```

Existing segment files are never modified. Segment queries combine all flushed
segments and mutable documents by document ID. Cross-segment BM25 ranking is
not implemented yet because each segment currently has local statistics.
Merging is manual; there is no automatic threshold or background merge policy.
Deleted segment documents are hidden by persisted tombstones until a manual
merge removes their old postings.

## Requirements

- Java 21
- The included Gradle wrapper

## Build and test

```bash
./gradlew build
./gradlew test
```

Run only the indexed-search tests:

```bash
./gradlew test --tests minisearch.IndexedSearchEngineTest
```

## Run

Run the small naive-search example:

```bash
./gradlew run
```

Compare prepared linear search with indexed lookup on the same generated corpus:

```bash
./gradlew runExperiment
```

## Experiments

Build the main classes once, then run an experiment directly:

```bash
./gradlew classes
java -cp build/classes/java/main minisearch.TopResultsExperiment
java -cp build/classes/java/main minisearch.AutocompleteExperiment
java -cp build/classes/java/main minisearch.PersistenceExperiment
java -cp build/classes/java/main minisearch.SegmentFlushExperiment
java -cp build/classes/java/main minisearch.SegmentMergeExperiment
java -cp build/classes/java/main minisearch.ConcurrentQueryExperiment
java -Xmx2g -cp build/classes/java/main minisearch.PubMedBenchmark data/pubmed-baseline
```

The experiments measure only the question they are intended to explore:

- Top-K compares full result sorting with bounded heap selection.
- Autocomplete compares a full vocabulary scan with a sorted-vocabulary
  lower-bound lookup. The public `suggest` method still uses the full scan.
- Persistence records single-file save time, load time, and file size.
- Segment flush records the cost and size of writing only new indexed data.
- Segment merge compares 1,000 small segments with one merged replacement.
- Concurrent queries measures stable-reader throughput and query latency while
  another thread indexes and flushes.
- PubMed loads 100K real citations from the local NCBI baseline files and
  records build, persistence, latency, and concurrent-query results.

See [docs/architecture.md](docs/architecture.md) for the measured baselines and
the reasoning behind each step, and [docs/learning-journal.md](docs/learning-journal.md)
for the learning record. The one-machine real-corpus results are in
[docs/benchmarks.md](docs/benchmarks.md).

## Current limits

- The loaded index is still entirely in memory.
- Punctuation is not removed, so `redis,` and `redis` are different terms.
- Autocomplete scans every indexed vocabulary term for each prefix request.
- Single-file persistence rewrites and reloads one complete index file.
- Segment merging is explicit and rewrites all persisted segments into one.
- Deletes and updates retain tombstones until a manual merge reclaims old data.
- Writes are serialized; queries read an immutable state published at their
  start and do not see partial flushes.
- Segment queries use document-ID order across segments, not global BM25 ranking.
- Adding the same document ID again is not supported as an update.
