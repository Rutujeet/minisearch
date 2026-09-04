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
from disk. Save rewrites the complete file. There is no WAL, segment format,
background flush, merge, compression, or recovery yet.

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
```

The experiments measure only the question they are intended to explore:

- Top-K compares full result sorting with bounded heap selection.
- Autocomplete compares a full vocabulary scan with a sorted-vocabulary
  lower-bound lookup. The public `suggest` method still uses the full scan.
- Persistence records single-file save time, load time, and file size.

See [docs/architecture.md](docs/architecture.md) for the measured baselines and
the reasoning behind each step, and [docs/learning-journal.md](docs/learning-journal.md)
for the learning record.

## Current limits

- The loaded index is still entirely in memory.
- Punctuation is not removed, so `redis,` and `redis` are different terms.
- Autocomplete scans every indexed vocabulary term for each prefix request.
- Persistence rewrites and reloads one complete index file.
- Adding the same document ID again is not supported as an update.
