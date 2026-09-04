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
