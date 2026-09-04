# Current Architecture

MiniSearch currently stores documents in memory.

Search is performed using a linear scan over every document.

For every query, each document's title and body are inspected.

There is currently no index or persistent storage.

## Inverted index performance

| Documents | Prepared scan | Index construction | Indexed query |
| ---: | ---: | ---: | ---: |
| 1,000 | 0.888 ms | 26.576 ms | 0.006 ms |
| 10,000 | 4.371 ms | 160.880 ms | 0.003 ms |
| 100,000 | 40.426 ms | 1319.405 ms | 0.004 ms |
