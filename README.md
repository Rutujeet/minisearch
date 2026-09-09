# MiniSearch

**A full-text search engine built from first principles in Java 21.**

MiniSearch implements the core mechanics behind text search without Lucene, Elasticsearch, OpenSearch, or a prebuilt indexing library: positional inverted indexes, BM25 ranking, phrase and Boolean queries, top-K retrieval, persistence, immutable index segments, tombstone-based updates/deletes, and concurrent query snapshots.

**100K PubMed citations · 1.33K docs/s indexing · 2.68 ms p95 ranked search · 446.7 queries/s with 4 readers**

---

## Benchmark

Measured on the first **100,000 PubMed 2026 baseline citations** with non-empty titles and abstracts.

| Metric | Result |
| --- | ---: |
| Documents | 100,000 |
| Indexing throughput | **1,331.5 docs/s** |
| Ranked search p50 / p95 / p99 | **0.532 / 2.679 / 2.854 ms** |
| Phrase search p95 | **1.214 ms** |
| Boolean search p95 | **1.710 ms** |
| Concurrent ranked throughput, 4 readers | **446.7 queries/s** |
| Persisted index size | 278.129 MB |
| Startup / load | 41.774 s |

Machine: Intel Core Ultra 7 155H, 22 logical CPUs, 30 GiB RAM, Linux amd64, JDK 21.0.12.

These are one-machine measurements, not performance guarantees. Full methodology and results are in [docs/benchmarks.md](docs/benchmarks.md).

---

## Architecture

~~~mermaid
flowchart LR
    D[Documents] --> T[Tokenize]
    T --> I[Positional inverted index]

    I --> P[Posting lists<br/>doc ID · TF · positions]
    P --> B[BM25 scoring]
    B --> K[Top-K heap]
    K --> R[Ranked results]

    I --> PH[Phrase queries]
    I --> BO[Boolean queries]

    I --> M[Mutable index]
    M -->|flush| S[Immutable segments]
    S --> Q[Query-start snapshot]
    S -->|manual merge| SM[Structural segment merge]

    U[Updates / deletes] --> TS[Tombstones]
    TS --> Q
~~~

A posting stores:

~~~text
document ID
term frequency
token positions
~~~

Posting lists are ordered by document ID, which enables linear intersection, union, and difference for Boolean queries.

Ranked queries use **BM25** with <code>k1 = 1.2</code> and <code>b = 0.75</code>. Limited searches maintain a bounded min-heap, reducing result selection from <code>O(M log M)</code> full sorting to <code>O(M log K)</code> while still scoring every matching document.

---

## Why it was built this way

MiniSearch was developed as an engineering progression rather than by starting from a finished search-engine architecture.

~~~text
scan every document
→ pre-tokenize documents
→ inverted index
→ term frequency
→ TF-IDF
→ BM25
→ positional postings
→ Boolean posting operations
→ top-K heap
→ persistence
→ immutable segments
→ structural segment merging
→ tombstone updates/deletes
→ concurrent query snapshots
~~~

Each step was introduced only after the previous implementation exposed a measured or tested limitation.

- Prepared linear search at 100K documents: **40.426 ms**
- Indexed rare-term lookup at 100K documents: **0.004 ms**
- Full sort of 100K matches: **26.203 ms**
- Top-K heap: **18.417 ms**
- Initial 100K-document segment merge: **130.186 s**
- Structural posting-list merge: **5.338 s**

The full evolution and experiments are documented in [docs/learning-journal.md](docs/learning-journal.md) and [docs/architecture.md](docs/architecture.md).

---

## Features

### Search

- BM25-ranked multi-term search
- Exact phrase queries using positional postings
- Boolean AND, OR, and AND NOT
- Deterministic ranking and tie-breaking
- Bounded top-K retrieval
- Case-insensitive prefix autocomplete

### Index

- Positional inverted index
- Term-frequency and document-frequency statistics
- Document-length statistics for BM25
- Sorted posting lists
- Versioned binary persistence

### Storage

- Immutable disk-backed segments
- Incremental segment flushes
- Explicit structural segment merging
- Tombstone-based deletes and updates
- Restart recovery from persisted state

### Concurrency

Queries use **query-start snapshot semantics**.

Published segments and query state are immutable for readers. A query sees either the state before a flush or the state after it, never a partially published segment.

Writes are serialized under a coarse lock while readers operate without locks on their published snapshot.

---

## HTTP API

MiniSearch exposes a small JSON API using Java's built-in <code>HttpServer</code>.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| <code>POST</code> | <code>/documents</code> | Add a document |
| <code>PUT</code> | <code>/documents/{id}</code> | Update a document |
| <code>DELETE</code> | <code>/documents/{id}</code> | Delete a document |
| <code>GET</code> | <code>/search?q=...&limit=10</code> | Ranked search |
| <code>GET</code> | <code>/suggest?q=...&limit=10</code> | Prefix suggestions |

Example:

~~~bash
curl -X POST localhost:8080/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "id": 1,
    "title": "Distributed Systems",
    "body": "Replication helps distributed systems survive failures."
  }'

curl "localhost:8080/search?q=distributed+systems&limit=10"
curl "localhost:8080/suggest?q=distr&limit=10"
~~~

---

## Run locally

Requires Java 21.

~~~bash
./gradlew clean test build
./gradlew run
~~~

The test suite covers indexing, ranking, BM25 regressions, phrase and Boolean queries, persistence, segment loading and merging, tombstones, autocomplete, concurrency, and the HTTP boundary.

---

## Docker

Build:

~~~bash
docker build -t minisearch .
~~~

Run:

~~~bash
docker run --rm \
  -p 8080:8080 \
  -v minisearch-data:/data \
  minisearch
~~~

The container exposes port 8080 and stores persisted segments under <code>/data/segments</code>. It uses a Java 21 build stage and a smaller Java 21 runtime stage.

---

## CI

GitHub Actions runs the Java 21 build and complete test suite on every configured CI run:

~~~bash
./gradlew clean test build
~~~

---

## Design tradeoffs

MiniSearch intentionally stops short of becoming a Lucene or Elasticsearch clone.

Current limitations include:

- Tokenization only lowercases and splits on whitespace; punctuation remains significant.
- Index data is loaded into memory for querying.
- Autocomplete scans the indexed vocabulary and is a known v1 latency limitation.
- Segment merging is explicit rather than scheduled in the background.
- Writes are serialized under one coarse lock.
- Cross-segment ranked search does not yet aggregate global BM25 collection statistics.
- There is no general query parser, fuzzy search, stemming, distributed search, vector search, or replication.

These are deliberate boundaries rather than features documented ahead of implementation.

---

## Project docs

- [docs/architecture.md](docs/architecture.md) — current architecture and measured design decisions
- [docs/learning-journal.md](docs/learning-journal.md) — evolution from naive scanning to the current engine
- [docs/benchmarks.md](docs/benchmarks.md) — benchmark methodology and PubMed results

---

## Tech

**Java 21 · Gradle · JUnit 5 · Gson · Java HttpServer · Docker · GitHub Actions**

The educationally important search and storage internals are implemented directly rather than delegated to an existing search engine.
