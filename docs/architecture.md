# Architecture

## Overview

MiniSearch is an in-memory Java search engine built in small steps. It keeps
documents by ID and an inverted index that maps each normalized term to a
posting list. A posting contains a document ID, that term's frequency, and
the token positions where it occurs in the document. Posting lists are kept in
ascending document-ID order.

```mermaid
flowchart TD
    A[Documents: ID, title, body] --> B[Lowercase and split on whitespace]
    B --> C[Count each term per document]
    C --> D[Inverted index: term → postings with positions]
    Q[Query] --> E[Lowercase and split on whitespace]
    E --> F[Look up each unique term]
    D --> F
    F --> G[Score matching documents with BM25]
    G --> H[Sort: score descending, document ID ascending]
    H --> R[Matching documents]
```

## Evolution

```mermaid
flowchart LR
    A[Scan every document] -->|Problem: query work grows with corpus size| B[Prepare documents once]
    B -->|Problem: still scan every document| C[Inverted index]
    C -->|Problem: matching is not ranking| D[Count matching query terms]
    D -->|Problem: all matches have equal weight| E[Store term frequency in postings]
    E -->|Problem: common terms overpower rare terms| F[TF-IDF ranking]
    F -->|Problem: repeated terms and long documents inflate scores| G[BM25 ranking]
    G -->|Problem: term order and adjacency are unknown| H[Positional postings]
    H -->|Problem: require or exclude non-adjacent terms| I[Boolean posting operations]
    I -->|Problem: only a few ranked results are needed| J[Bounded top-K heap]
    J -->|Problem: users type incomplete terms| K[Scan indexed vocabulary]
    K -->|Problem: restart loses the index| L[Single-file persistence]
```

| Stage | Problem | Smallest useful approach |
| --- | --- | --- |
| Prepared documents | Re-tokenizing content on every query wastes work. | Normalize title and body once when indexing. |
| Inverted index | Queries still inspect unrelated documents. | Map each term to matching document postings. |
| Multi-term ranking | OR matches have no useful order. | Rank by matched query terms. |
| Term frequency | One and many occurrences are treated the same. | Store one posting per document-term with its TF. |
| TF-IDF | Common terms can dominate rare, useful terms. | Score each posting as `TF × ln(N / df)`. |
| BM25 | Raw TF-IDF gives unlimited weight to repetition and favors long documents. | Saturate term frequency and normalize by document length. |
| Positional postings | A term-only index cannot tell whether query terms are adjacent and ordered. | Store every token position in each posting. |
| Boolean operations | OR matching and phrases cannot express required or excluded terms. | Merge sorted postings for intersection, union, and difference. |
| Top-K results | Sorting every match wastes work when a caller asks for only a few results. | Keep the best K scored documents in a bounded min-heap. |
| Autocomplete | Exact-term lookup cannot suggest terms for an incomplete prefix. | Scan the existing indexed vocabulary for matching prefixes. |
| Persistence | Process restarts lose all indexed state. | Save and load one complete versioned binary index file. |

## Current ranking

For every unique query term, MiniSearch reads its postings and adds a BM25
score. BM25 gives a larger score to rare terms, but repeated occurrences add
less value each time. It also compares a document's token count with the
average token count in the collection, so a match in a long document needs
more evidence than the same match in a short document.

MiniSearch uses `k1 = 1.2` to control term-frequency saturation and
`b = 0.75` to control document-length normalization. It stores each document's
token length and the total indexed token count to calculate the average.

Results with the same score are ordered by lower document ID.

## Limited ranked results

### Problem

The caller requested only the top 10 results, but search originally fully
sorted every matching document.

### Evidence

Full sort + take 10:

| Matches | Average query time |
| ---: | ---: |
| 1K | 1.581 ms |
| 10K | 10.246 ms |
| 100K | 26.203 ms |

The experiment includes scoring as well as sorting, so it does not isolate
sorting cost. At this scale the latency is still reasonable; the concern is
that fully ordering M results performs work the caller does not need.

### New design

`search(query, limit)` still scores every matching document with BM25. While
scoring, it keeps at most `limit` candidates in a min-heap. The heap root is
the weakest current result: the lowest score, or the highest document ID when
scores tie. A better candidate replaces that root. The final winners are then
sorted by score descending and document ID ascending.

This changes selection from sorting all M matches, `O(M log M)`, to maintaining
at most K winners, `O(M log K)`. It does not avoid BM25 scoring for all M
matching documents.

### Comparison

Both experiments use the same generated corpus, query (`java`), limit (10),
three warmup runs, and five measured runs.

| Matches | Full sort + take 10 | Top-K heap |
| ---: | ---: | ---: |
| 1K | 1.581 ms | 1.426 ms |
| 10K | 10.246 ms | 9.433 ms |
| 100K | 26.203 ms | 18.417 ms |

At 100K matches the improvement is useful but not dramatic because scoring is
still required for every match. The heap removes only the unnecessary complete
ordering of losing results.

## Autocomplete

`suggest(prefix, limit)` returns known indexed terms that start with a
case-insensitive prefix. It scans the keys of the existing inverted index,
sorts matching terms alphabetically, and returns at most `limit` suggestions.
There is no separate vocabulary set, frequency ranking, fuzzy matching, or
prefix data structure.

### Realistic typing workload

The first 100K-term scan was under 10 ms for one suggestion. Autocomplete is
called once per keystroke, however. This experiment measures one
`suggest("distributed", 10)` call and a sequence of 11 ordinary calls for:
`d`, `di`, `dis`, `dist`, `distr`, `distri`, `distrib`, `distribu`,
`distribut`, `distribute`, and `distributed`.

The vocabulary always contains ten `distributed...` terms and otherwise uses
unique `token...` terms. Index construction happens before timing; each case
uses three warmup runs and five measured runs.

| Vocabulary | Single query | Full typing sequence |
| ---: | ---: | ---: |
| 100K | 3.947 ms | 43.064 ms |
| 500K | 15.106 ms | 173.659 ms |
| 1M | 29.818 ms | 339.551 ms |

The implementation is still a full vocabulary scan, `O(V)`, for every call.
At 1M terms the single query remains under 30 ms, but the typing sequence
performs eleven scans and reaches about 340 ms. The experiment exposes that
cost without choosing a replacement data structure yet.

## Persistence

`IndexStorage` saves and loads the complete in-memory index in one binary file.
The file starts with a magic value and format version, followed by documents
and their token lengths, total indexed token length, and term postings with
term frequency and positions. This is enough to reconstruct normal, phrase,
and Boolean search exactly after a restart.

Average document length and sorted vocabulary are derived after loading. They
are not stored because they can be rebuilt from persisted source state.

| Documents | Save time | Load time | File size |
| ---: | ---: | ---: | ---: |
| 1K | 69.067 ms | 35.789 ms | 0.250 MB |
| 10K | 312.438 ms | 175.281 ms | 2.523 MB |
| 100K | 2672.157 ms | 1592.068 ms | 25.428 MB |

The experiment builds the deterministic corpus before timing. Save and load
measure only the single-file operation. This design has no WAL, segments,
background flush, merge, compression, or recovery. Saving a changed index
rewrites the whole file, and loading restores the whole file into memory.

## Phrase search

`searchPhrase("distributed systems")` looks for those terms next to each
other and in that order. It first uses the inverted index to find documents
that contain every phrase term. It then checks their stored positions. For
example, positions `0` for `distributed` and `1` for `systems` match; positions
`0` and `4` do not.

Title and body tokens are indexed separately for phrase matching. A one-token
gap between the two fields prevents a phrase from matching across their
boundary. Phrase results are returned by document ID; they do not receive a
BM25 boost.

## Boolean search

`searchAnd("java", "concurrency")` returns documents containing both terms.
`searchOr` returns documents containing either term. `searchAndNot("java",
"spring")` returns documents containing `java` but not `spring`.

These operations merge sorted posting lists with forward pointers. They return
documents in ascending document-ID order and do not use BM25, because they
answer whether a document satisfies a condition rather than how relevant it is.

## Current limits

- All documents and index data are in memory.
- Tokenization only lowercases and splits on whitespace; punctuation remains.
- Keyword queries use OR semantics. Exact phrase queries use the separate
  `searchPhrase` operation. Boolean queries use `searchAnd`, `searchOr`, and
  `searchAndNot`; quotation-mark parsing, parentheses, and a general query
  parser are not supported.
- Autocomplete scans every indexed vocabulary term for each prefix request.
- Persistence rewrites and reloads one complete index file.
- Re-indexing an existing document ID is not supported as an update.

## Inverted-index performance

| Documents | Prepared scan | Index construction | Indexed query |
| ---: | ---: | ---: | ---: |
| 1,000 | 0.888 ms | 26.576 ms | 0.006 ms |
| 10,000 | 4.371 ms | 160.880 ms | 0.003 ms |
| 100,000 | 40.426 ms | 1319.405 ms | 0.004 ms |
