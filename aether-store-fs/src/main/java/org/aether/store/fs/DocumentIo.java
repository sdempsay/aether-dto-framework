package org.aether.store.fs;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.google.gson.Gson;

import org.aether.store.AetherAck;
import org.aether.store.fs.StoredDocument.StoredMetadata;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Atomic JSON document read/write helpers (exceptional I/O).
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
final class DocumentIo {
    private final Gson gson;

    DocumentIo(final Gson gson) {
        this.gson = gson;
    }

    ExceptionalResponse<StoredDocument> read(final Path path) {
        return ExceptionalSupplier.of(() -> {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return gson.fromJson(reader, StoredDocument.class);
            }
        }).execute();
    }

    ExceptionalResponse<AetherAck> writeAtomic(final Path path, final StoredDocument document) {
        return ExceptionalSupplier.of(() -> {
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
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException ex) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return AetherAck.INSTANCE;
        }).execute();
    }

    ExceptionalResponse<AetherAck> deleteIfExists(final Path path) {
        return ExceptionalSupplier.of(() -> {
            Files.deleteIfExists(path);
            return AetherAck.INSTANCE;
        }).execute();
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
