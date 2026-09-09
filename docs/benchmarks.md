# Benchmarks

These results are one-machine measurements, not MiniSearch performance
guarantees. The commands, corpus selection, warmups, and measured-run counts
live beside the corresponding experiment classes.

## PubMed 100K benchmark

Dataset: NCBI PubMed 2026 baseline. The benchmark loads the first 100,000
citations with a non-empty `ArticleTitle` and `AbstractText` from baseline XML
files 0001 through 0007. PMID becomes the document ID, title becomes the
title, and abstract sections are joined into the body.

Machine: Intel Core Ultra 7 155H, 22 logical CPUs, 30 GiB RAM, Linux amd64,
JDK 21.0.12.

| Metric | Result |
| --- | ---: |
| Compressed source XML read | 130.416 MB |
| Source load | 16509.123 ms |
| Index build | 75100.971 ms |
| Indexing throughput | 1331.5 docs/s |
| Persisted index | 278.129 MB |
| Persist | 72851.338 ms |
| Startup/load | 41774.080 ms |
| Ranked `cancer` p50 / p95 / p99 | 0.532 / 2.679 / 2.854 ms |
| Phrase `randomized controlled` p50 / p95 / p99 | 0.768 / 1.214 / 1.305 ms |
| Boolean `cancer AND therapy` p50 / p95 / p99 | 0.273 / 1.710 / 1.724 ms |
| Autocomplete `cardio` p50 / p95 / p99 | 50.238 / 65.139 / 74.385 ms |
| Concurrent ranked throughput, 4 readers | 446.7 queries/s |

Latency uses three warmups and 100 measured runs. The compressed PubMed XML
and the uncompressed MiniSearch index are different representations, so their
sizes are not a storage-expansion comparison.

Autocomplete scans the full vocabulary for every prefix. Its 65.139 ms p95 is
a known limitation of v1, not a reason to add another prefix data structure.

The downloaded corpus stays local and is ignored by Git. NCBI's baseline notes
that some abstracts may be copyrighted; use it for local benchmarking and do
not redistribute the corpus without reviewing its terms. See
[dataset research](dataset-research.md) for the official source links.
