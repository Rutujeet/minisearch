package minisearch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Stores document IDs that are hidden from older immutable segments. */
final class TombstoneStorage {
    private static final int MAGIC = 0x4D535458;
    private static final int VERSION = 1;

    Map<Integer, Integer> load(Path path) throws IOException {
        if (Files.notExists(path)) {
            return new HashMap<>();
        }

        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unsupported MiniSearch tombstone file");
            }
            Map<Integer, Integer> tombstones = new HashMap<>();
            int count = input.readInt();
            for (int index = 0; index < count; index++) {
                tombstones.put(input.readInt(), input.readInt());
            }
            return tombstones;
        }
    }

    void save(Map<Integer, Integer> tombstones, Path path) throws IOException {
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(tombstones.size());
            for (Map.Entry<Integer, Integer> tombstone : tombstones.entrySet()) {
                output.writeInt(tombstone.getKey());
                output.writeInt(tombstone.getValue());
            }
        }
    }
}
