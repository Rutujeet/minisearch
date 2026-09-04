# Learning Journal

## TF-IDF ranking

**Problem:** Raw term frequency treated every query term equally. Very common
terms could overpower rarer, more informative terms.

**Evidence:** For `java distributed`, a document repeating `java` five times
ranked above the only document containing `distributed`.

**New information:** Document frequency — how many documents contain each term.

**New design:** Weight term frequency by inverse document frequency.

**Tradeoff:** Ranking now depends on collection-wide statistics, so adding or
removing documents can theoretically change scores across the corpus.

## BM25 ranking

### Problem

TF-IDF improved weighting of common versus rare terms, but raw term frequency
still grew linearly. A long document repeating a query term many times could
outrank a much shorter, focused document.

### Evidence

For `distributed systems`, the corpus contained `Focused` with `distributed
systems architecture`, `Broad` with `distributed` repeated ten times plus two
`systems` tokens and a long unrelated tail, and three unrelated documents.
TF-IDF ranked `Broad, Focused`; BM25 ranks `Focused, Broad`.

### Alternatives considered

- Sublinear/log-scaled TF.
- Explicit document-length normalization.
- BM25.

### New design

BM25 combines smoothed IDF with saturated term-frequency contribution and
document-length normalization. The index tracks each document's token length
and total indexed token count to calculate average document length. Its
defaults are `k1 = 1.2` and `b = 0.75`.

### Tradeoffs

Ranking requires more collection statistics and two parameters, `k1` and `b`.
Scores are more complex to explain than raw TF-IDF, but correspond better to
the relevance problem exposed by the test corpus.

## Boolean query operations

### Problem

Normal search uses OR-like matching and phrase search requires adjacency, but
there was no way to require non-adjacent terms or exclude a term.

### First implementation

The first AND intersection checked each document ID in one posting list with
`contains()` on the other list.

### Evidence

With two sorted lists of 50,000 IDs and 37,500 shared IDs, the naive scan took
1,231 ms in a one-off local run. A two-pointer scan over the same lists took
966 ms. This is not a benchmark, but it shows the repeated list scans are real
work.

### New design

Posting lists are maintained in ascending document-ID order. Intersection,
union, and difference use forward pointer scans. `searchAnd`, `searchOr`, and
`searchAndNot` return their Boolean matches in document-ID order without BM25
scoring.

### Tradeoffs

Sortedness is now an index invariant. The incremental index sorts a posting
list after an out-of-order append; batch indexing can be added if indexing
cost becomes a problem.
