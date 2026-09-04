package minisearch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stores and loads a complete MiniSearch index in one versioned binary file. */
public class IndexStorage {
    private static final int MAGIC = 0x4D534958;
    private static final int VERSION = 1;

    /**
     * Writes the engine's documents, document lengths, and postings to {@code path}.
     *
     * @param engine the engine whose index state is written
     * @param path the destination binary file
     * @throws IOException if the file cannot be written
     */
    public void save(IndexedSearchEngine engine, Path path) throws IOException {
        IndexSnapshot snapshot = engine.snapshot();
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);

            List<StoredDocument> documents = new ArrayList<>(snapshot.documents());
            documents.sort(Comparator.comparingInt(stored -> stored.document().id()));
            output.writeInt(documents.size());
            for (StoredDocument stored : documents) {
                output.writeInt(stored.document().id());
                output.writeUTF(stored.document().title());
                output.writeUTF(stored.document().body());
                output.writeInt(stored.documentLength());
            }

            output.writeLong(snapshot.totalDocumentLength());
            List<String> terms = new ArrayList<>(snapshot.postings().keySet());
            terms.sort(String::compareTo);
            output.writeInt(terms.size());
            for (String term : terms) {
                output.writeUTF(term);
                List<Posting> postings = snapshot.postings().get(term);
                output.writeInt(postings.size());
                for (Posting posting : postings) {
                    output.writeInt(posting.documentId());
                    output.writeInt(posting.termFrequency());
                    output.writeInt(posting.positions().size());
                    for (Integer position : posting.positions()) {
                        output.writeInt(position);
                    }
                }
            }
        }
    }

    /**
     * Loads a complete MiniSearch index from {@code path}.
     *
     * @param path the source binary file
     * @return an engine reconstructed from the stored state
     * @throws IOException if the file cannot be read or its format is unsupported
     */
    public IndexedSearchEngine load(Path path) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Not a MiniSearch index file");
            }
            if (input.readInt() != VERSION) {
                throw new IOException("Unsupported MiniSearch index version");
            }

            List<StoredDocument> documents = new ArrayList<>();
            int documentCount = input.readInt();
            for (int index = 0; index < documentCount; index++) {
                Document document = new Document(input.readInt(), input.readUTF(), input.readUTF());
                documents.add(new StoredDocument(document, input.readInt()));
            }

            long totalDocumentLength = input.readLong();
            Map<String, List<Posting>> postings = new HashMap<>();
            int termCount = input.readInt();
            for (int termIndex = 0; termIndex < termCount; termIndex++) {
                String term = input.readUTF();
                List<Posting> termPostings = new ArrayList<>();
                int postingCount = input.readInt();
                for (int postingIndex = 0; postingIndex < postingCount; postingIndex++) {
                    int documentId = input.readInt();
                    int termFrequency = input.readInt();
                    List<Integer> positions = new ArrayList<>();
                    int positionCount = input.readInt();
                    for (int positionIndex = 0; positionIndex < positionCount; positionIndex++) {
                        positions.add(input.readInt());
                    }
                    termPostings.add(new Posting(documentId, termFrequency, positions));
                }
                postings.put(term, termPostings);
            }
            return IndexedSearchEngine.fromSnapshot(new IndexSnapshot(documents, postings, totalDocumentLength));
        }
    }
}
