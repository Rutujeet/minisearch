package minisearch;

import java.nio.file.Path;

record Segment(Path path, IndexedSearchEngine index) {
}
