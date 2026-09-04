package minisearch;

import java.util.List;
import java.util.Map;

record IndexSnapshot(
        List<StoredDocument> documents,
        Map<String, List<Posting>> postings,
        long totalDocumentLength) {
}

record StoredDocument(Document document, int documentLength) {
}
