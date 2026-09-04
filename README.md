# MiniSearch

MiniSearch is a small Java project for learning how search engines evolve from
a document scan to an inverted index.

## What it does

MiniSearch stores documents in memory. A document has an ID, title, and body.

It currently has two search implementations:

- `NaiveSearchEngine` is the baseline. It scans every prepared document and
  term for each query.
- `IndexedSearchEngine` builds an inverted index: `term -> document IDs`.
  It supports one or more whitespace-separated query terms.

Multiple query terms use OR behavior. A document matches when it contains at
least one query term. Results are ordered by the number of distinct query terms
matched, then by document ID.

For example, `java concurrency` returns documents containing `java`,
`concurrency`, or both. A document containing both appears first.

## Requirements

- Java 21
- The included Gradle wrapper

## Build

```bash
./gradlew build
```

## Test

```bash
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

The experiment reports separate index-construction and query times for 1K, 10K,
and 100K documents.

## Current limits

- Documents and the index exist only in memory.
- Tokenization is deliberately basic: lowercase text and split on whitespace.
- Punctuation is not removed, so `redis,` is different from `redis`.
- There is no phrase search, AND search, term frequency, or relevance model.
- Adding the same document ID again is not supported as an update operation.
