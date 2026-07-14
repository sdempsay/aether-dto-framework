package org.aether.store.fs;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.google.gson.Gson;

import org.aether.store.fs.StoredDocument.StoredMetadata;

/**
 * Atomic JSON document read/write helpers.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
final class DocumentIo {
    private final Gson gson;

    DocumentIo(final Gson gson) {
        this.gson = gson;
    }

    StoredDocument read(final Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, StoredDocument.class);
        }
    }

    void writeAtomic(final Path path, final StoredDocument document) throws IOException {
        Files.createDirectories(path.getParent());
        final Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(
                temp,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            gson.toJson(document, writer);
        }
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException ex) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    void deleteIfExists(final Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    boolean exists(final Path path) {
        return Files.isRegularFile(path);
    }

    static StoredMetadata toWire(final org.aether.store.AetherResourceMetadata metadata) {
        return new StoredMetadata(
                metadata.id(),
                metadata.createdAt(),
                metadata.updatedAt(),
                metadata.version(),
                metadata.createdBy(),
                metadata.updatedBy());
    }
}
