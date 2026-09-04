package minisearch;

import java.util.List;

public record PreparedDocument(Document document, List<String> terms) {
}
