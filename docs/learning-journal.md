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

## Naive autocomplete

### Problem

Exact-term postings cannot directly answer which indexed terms start with a
partial prefix such as `distr`.

### First implementation

Autocomplete scans the existing inverted-index keys, keeps terms starting with
the normalized prefix, sorts those matches alphabetically, and applies the
limit. It does not maintain a second vocabulary structure.

### Evidence

For `suggest("distributed", 10)`, with three warmup runs and five measured
runs:

| Unique terms | Average suggestion time |
| ---: | ---: |
| 1K | 0.235 ms |
| 10K | 2.934 ms |
| 100K | 8.938 ms |

At 100K unique terms, the full vocabulary scan remained under 10 ms in this
exploratory benchmark, so a more complex prefix data structure was not yet
justified.

## Single-file persistence

### Problem

The complete index lived only in RAM, so stopping the process lost all
searchable state.

### New design

`IndexStorage` writes one versioned binary file using `DataOutputStream` and
reconstructs an engine using `DataInputStream`. It persists documents,
document lengths, total indexed token length, terms, postings, term frequency,
and positions. Average document length and sorted vocabulary are rebuilt from
that state.

### Evidence

| Documents | Save | Load | File size |
| ---: | ---: | ---: | ---: |
| 1K | 69.067 ms | 35.789 ms | 0.250 MB |
| 10K | 312.438 ms | 175.281 ms | 2.523 MB |
| 100K | 2672.157 ms | 1592.068 ms | 25.428 MB |

### Tradeoffs

The single file is easy to understand and supports exact round trips, but any
save rewrites the entire index and any load reconstructs the complete in-memory
structure. More incremental storage is not justified until that becomes a
measured problem.

## Snapshot update cost

### Problem

Single-file persistence survives restarts, but it is unclear whether saving a
small update costs proportional to the update or to the whole loaded index.

### Evidence

Each measurement built and saved 100K documents, indexed the added documents
before timing, then measured one more `save()`.

| Existing documents | Added documents | Re-save | Final file size |
| ---: | ---: | ---: | ---: |
| 100K | 1 | 3234.089 ms | 25.428 MB |
| 100K | 100 | 2832.599 ms | 25.454 MB |
| 100K | 1K | 2673.055 ms | 25.684 MB |

### Conclusion

The tiny updates changed file size only slightly, but each save still took
whole-index time because the snapshot serializer rewrote the complete file.
Persistence cost currently depends primarily on total index size, not on the
amount of newly indexed data.

## Immutable segment flushes

### Problem

Small updates to a large snapshot still rewrote the complete persisted index.

### New design

`SegmentedSearchEngine` writes the current mutable index to a new immutable
segment file when `flush()` is called. It then searches that segment, earlier
segments, and the new mutable index together. Older segment files are not
modified.

### Evidence

After flushing one 100K-document segment, later flushes wrote only the new
documents:

| Added documents | Flush | New segment size |
| ---: | ---: | ---: |
| 1 | 0.922 ms | 0.000 MB |
| 100 | 3.882 ms | 0.026 MB |
| 1K | 35.096 ms | 0.257 MB |

### Tradeoffs

Write cost now follows new data rather than total historical data. Queries must
visit every segment, and BM25 scores are currently ordered by document ID
across segments because collection-wide statistics have not been aggregated.
Merge and compaction are intentionally not implemented.

## Segment-count read cost

### Problem

Immutable segments reduce write amplification, but each segment is another
independent index that queries and restart logic must inspect.

### Evidence

The corpus remained fixed at 100K documents. The rare term appears in one
document; the common term appears in every document. Each query used three
warmup runs and five measured runs.

| Segments | Docs/segment | Rare query | Common query | Load time |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 100K | 0.176 ms | 130.725 ms | 4579.424 ms |
| 10 | 10K | 0.167 ms | 49.180 ms | 3682.346 ms |
| 100 | 1K | 0.153 ms | 37.834 ms | 3293.492 ms |
| 1K | 100 | 3.005 ms | 70.268 ms | 3511.573 ms |

### Tradeoffs

At 1,000 segments, even the rare query must visit many independent indexes and
is noticeably slower than the smaller-segment-count cases. This is read
amplification: immutable segments fix whole-snapshot rewrites but add read and
startup work as segment count grows.

## Manual segment merge

### Problem

The fixed 100K-document corpus showed visible rare-query overhead at 1,000
segments. Most segments had no match, but every segment still had to be
checked.

### New design

`mergeAllSegments()` rebuilds all persisted documents into one new immutable
segment using the existing indexing path. It writes the replacement before
registering it and deleting the old segment files. The mutable index stays
separate.

### Evidence

| State | Segments | Rare query | Common query | Load |
| --- | ---: | ---: | ---: | ---: |
| Before merge | 1K | 1.953 ms | 58.300 ms | 2660.841 ms |
| After merge | 1 | 0.011 ms | 37.433 ms | 1639.838 ms |

Merging 1,000 segments containing 100K documents took 51856.969 ms and wrote
a 13.578 MB replacement segment.

### Tradeoffs

Manual merging reduces read amplification, but it reads and rewrites old data,
creating write amplification again. There is no automatic merge policy,
background scheduler, or global cross-segment BM25 statistics yet.

## Manual merge phase profile

### Problem

The first manual merge reduced 1,000 segments to one, but took about 51.9
seconds for 100K documents. We needed to find out whether the time came from
opening many files or rebuilding already-indexed content.

### Evidence

The engine loads segment files when it opens, before `mergeAllSegments()`
runs. That startup work is therefore reported separately. The merge phase
reads stored documents, indexes them again, writes the replacement segment,
then deletes the old files.

| Source layout | Load source segments | Read documents | Re-index | Write | Cleanup | Merge total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1K × 100 docs | 2540.425 ms | 101.558 ms | 125542.718 ms | 4521.357 ms | 20.768 ms | 130186.401 ms |
| 100 × 1K docs | 2264.701 ms | 28.291 ms | 78561.806 ms | 2982.371 ms | 3.889 ms | 81576.357 ms |

The absolute times are exploratory and vary between runs. In both layouts,
re-indexing dominates. Source loading and document recovery are much smaller,
so the main pain is recomputing tokenization, term frequency, positions, and
postings that the source segments already store.

### Conclusion

Before adding a merge policy or moving work to a background thread, the next
question is whether MiniSearch can merge indexed state directly in memory.
That would remove redundant re-indexing while keeping the same read-all and
write-one-segment behavior.

## Structural segment merge

### Problem

The phase profile showed that manual merging spent 78–125 seconds re-indexing
documents that already had tokens, term frequencies, positions, document
lengths, and postings stored in their source segments.

### New design

Merge consumes `IndexSnapshot` data directly. It combines documents and their
stored lengths, sums total document length, and merges each term's sorted
posting lists with forward pointers. Duplicate document IDs fail clearly.
The tokenizer is not part of the merge path.

### Evidence

| Source layout | Structural merge | Write | Cleanup | Merge total |
| --- | ---: | ---: | ---: | ---: |
| 1K × 100 docs | 2360.772 ms | 2964.057 ms | 12.765 ms | 5337.594 ms |
| 100 × 1K docs | 294.436 ms | 2884.467 ms | 4.303 ms | 3183.206 ms |

This replaces the earlier 130.186-second and 81.576-second rebuild totals in
the phase-profile runs. A control index built normally from the same documents
matches the merged segment for BM25 search, phrase search, Boolean search, and
autocomplete.

### Tradeoffs

Merge logic now understands the snapshot structure, which couples it to the
persisted index representation. It still loads source index state into memory,
iteratively merges postings, and writes a complete replacement segment. No
streaming merge, k-way posting merge, automatic policy, or background work has
been added.

## Updates and deletes with tombstones

### Problem

An immutable segment cannot remove or replace a document after it has been
written. Editing the old file would break the immutable-segment model.

### New design

`delete(id)` records a tombstone and hides that ID in existing segments.
`update(document)` deletes the old ID and adds its replacement to the mutable
index. Tombstones survive restart in a small versioned file. They carry the
latest segment number they affect, so a newly flushed replacement with the
same ID stays visible.

### Evidence

Tests verify that deleted documents disappear from normal, phrase, and Boolean
queries; autocomplete removes a term only when its last document is deleted;
updates replace old searchable content; and tombstones survive restart.
Merging a deleted document out of a segment removes its postings, after which
the tombstone state is cleared.

### Tradeoffs

Queries inspect tombstones in addition to segments, and deleted data occupies
space until a manual merge. There are no document versions, MVCC, or automatic
merge policy.

## Concurrent queries during indexing

### Problem

The mutable index, segment list, and tombstones could be changed while a query
was reading them. A flush also needed to publish a completed segment without
exposing a partially written file.

### New design

The engine atomically publishes an immutable query state: segment list,
tombstones, and frozen mutable-index view. A query keeps the state it reads at
its start. Writers use one coarse lock and make a structural mutable-index copy
before changing it, so no published index is later mutated.

### Evidence

An eight-reader, one-writer fixed-iteration test adds documents, flushes
periodically, and updates a document while readers run normal, phrase,
Boolean, and autocomplete queries. It completes without exceptions or partial
results. On a stable 10K-document segment, the exploratory throughput run was:

| Readers | Throughput |
| ---: | ---: |
| 1 | 119.6 queries/s |
| 4 | 398.1 queries/s |
| 8 | 490.6 queries/s |

Average query latency was 7.278 ms while stable and 7.929 ms while a writer
indexed 100 documents and flushed them in the same run.

### Tradeoffs

Writes are serialized, and every mutable write copies the current mutable
index. That is deliberately simple and can become expensive for large mutable
batches. There are no indexing workers, queues, sharding, or lock-free writes.
