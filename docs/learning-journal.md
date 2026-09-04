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
