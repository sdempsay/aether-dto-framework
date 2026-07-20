package org.dempsay.aether.store.fs;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.google.gson.Gson;

import org.dempsay.aether.failure.AetherException;
import org.dempsay.aether.failure.AetherFailure;
import org.dempsay.aether.store.api.AetherAck;
import org.dempsay.aether.store.fs.StoredDocument.StoredMetadata;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalResource;
import org.dempsay.utils.exceptional.api.ExceptionalResourceAction;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Atomic JSON document read/write helpers using exceptional resource patterns.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
final class DocumentIo {
    private final Gson gson;

    DocumentIo(final Gson gson) {
        this.gson = gson;
    }

    /**
     * Reads a stored document. Callers map failure to {@code AetherFailure.NotFound}
     * (or Internal) for the store listener.
     *
     * @param path document path
     * @return document or exceptional failure
     */
    ExceptionalResponse<StoredDocument> read(final Path path) {
        return ExceptionalResource.of(
                () -> Files.newBufferedReader(path, StandardCharsets.UTF_8),
                reader -> gson.fromJson(reader, StoredDocument.class)).execute();
    }

    ExceptionalResponse<AetherAck> writeAtomic(final Path path, final StoredDocument document) {
        return ExceptionalSupplier.of(() -> {
            Files.createDirectories(path.getParent());
            final Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            final boolean wrote = ExceptionalResourceAction.of(
                    () -> Files.newBufferedWriter(
                            temp,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE),
                    writer -> gson.toJson(document, writer)).execute();
            if (!wrote) {
                throw new AetherException(AetherFailure.Internal, "Failed to write temp document " + temp);
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

    static StoredMetadata toWire(final org.dempsay.aether.store.api.AetherResourceMetadata metadata) {
        return new StoredMetadata(
                metadata.id(),
                metadata.createdAt(),
                metadata.updatedAt(),
                metadata.version(),
                metadata.createdBy(),
                metadata.updatedBy());
    }
}
