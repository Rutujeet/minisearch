# Architecture

## Overview

MiniSearch is an in-memory Java search engine built in small steps. It keeps
documents by ID and an inverted index that maps each normalized term to a
posting list. A posting contains a document ID and that term's frequency in
the document.

```mermaid
flowchart TD
    A[Documents: ID, title, body] --> B[Lowercase and split on whitespace]
    B --> C[Count each term per document]
    C --> D[Inverted index: term → postings]
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
```

| Stage | Problem | Smallest useful approach |
| --- | --- | --- |
| Prepared documents | Re-tokenizing content on every query wastes work. | Normalize title and body once when indexing. |
| Inverted index | Queries still inspect unrelated documents. | Map each term to matching document postings. |
| Multi-term ranking | OR matches have no useful order. | Rank by matched query terms. |
| Term frequency | One and many occurrences are treated the same. | Store one posting per document-term with its TF. |
| TF-IDF | Common terms can dominate rare, useful terms. | Score each posting as `TF × ln(N / df)`. |
| BM25 | Raw TF-IDF gives unlimited weight to repetition and favors long documents. | Saturate term frequency and normalize by document length. |

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

## Current limits

- All documents and index data are in memory.
- Tokenization only lowercases and splits on whitespace; punctuation remains.
- Queries use OR semantics; phrase and AND queries are not supported.
- Re-indexing an existing document ID is not supported as an update.

## Inverted-index performance

| Documents | Prepared scan | Index construction | Indexed query |
| ---: | ---: | ---: | ---: |
| 1,000 | 0.888 ms | 26.576 ms | 0.006 ms |
| 10,000 | 4.371 ms | 160.880 ms | 0.003 ms |
| 100,000 | 40.426 ms | 1319.405 ms | 0.004 ms |
