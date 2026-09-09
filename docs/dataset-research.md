# Dataset research

## Recommendation: PubMed annual baseline

Use the first 100,000 eligible records from the [2026 PubMed baseline
directory](https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/). PubMed is a public
NCBI/NLM corpus of biomedical citations and abstracts. NLM publishes one
complete baseline each year as XML and makes it available from its FTP service.
The [PubMed download documentation](https://pubmed.ncbi.nlm.nih.gov/download/)
describes the baseline and links to the current format documentation.

The first files are small enough to start simply:

```
https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/pubmed26n0001.xml.gz
https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/pubmed26n0002.xml.gz
https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/pubmed26n0003.xml.gz
...
```

Read files in lexical order and stop once 100,000 records with a non-empty
title and abstract have been accepted. Record the exact filenames and their
published MD5 files with the benchmark result; the baseline changes annually.

### MiniSearch mapping

| MiniSearch field | PubMed XML field | Rule |
| --- | --- | --- |
| `id` | `PMID` | Parse the numeric PMID. |
| `title` | `ArticleTitle` | Keep its text content. |
| `body` | `Abstract/AbstractText` | Join every abstract section with a space. |

This provides natural title-and-body documents without inventing titles or
splitting a larger text into artificial records. The files are gzip-compressed
XML, so ingestion can use Java's `GZIPInputStream` and StAX XML reader; it
needs no new library or preprocessing tool.

### Terms and attribution

This is not a blanket open-content license. NLM says a signed agreement is not
needed for its public data, but also warns that publication abstracts can be
protected by copyright. Attribute NLM, use the corpus locally for benchmarking,
and do not commit, redistribute, or publish the raw corpus or its extracted
text without a rights review. See NLM's [data copyright and download
terms](https://www.nlm.nih.gov/databases/download.html) and the baseline
[README](https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/README.txt).

## Considered fallback: Simple English Wikipedia

[Simple English Wikipedia's pages-articles dump](https://dumps.wikimedia.org/simplewiki/latest/simplewiki-latest-pages-articles.xml.bz2)
offers stable page IDs, page titles, and article text under Wikimedia's
[CC BY-SA and GFDL terms](https://dumps.wikimedia.org/legal.html). It is a good
choice when a clearly reusable general-language corpus matters more than
ingestion simplicity.

It is not the first choice for this benchmark: the current page-content dump is
`bz2`, not gzip, and the body is wikitext that needs cleanup before indexing.
PubMed therefore better fits the project's no-new-dependency, small-ingester
goal. Wikimedia's [dump documentation](https://meta.wikimedia.org/wiki/Data_dumps/What's_available_for_download)
lists page-content XML as `pages-articles.xml.bz2` and notes that its gzip
abstract dumps were discontinued in February 2025.
