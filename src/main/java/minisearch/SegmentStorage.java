package minisearch;

import java.io.IOException;
import java.nio.file.Path;

final class SegmentStorage {
    private final IndexStorage indexStorage = new IndexStorage();

    Segment write(IndexedSearchEngine index, Path path) throws IOException {
        indexStorage.save(index, path);
        return new Segment(path, index);
    }

    Segment load(Path path) throws IOException {
        return new Segment(path, indexStorage.load(path));
    }
}
