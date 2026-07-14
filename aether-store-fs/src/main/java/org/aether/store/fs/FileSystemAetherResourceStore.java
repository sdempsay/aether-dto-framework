package org.aether.store.fs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.aether.access.AetherPrincipal;
import org.aether.failure.AetherFailure;
import org.aether.failure.AetherResponses;
import org.aether.store.AbstractAetherResourceStore;
import org.aether.store.AetherAck;
import org.aether.store.AetherPersisted;
import org.aether.store.DefaultAetherPersisted;
import org.aether.store.DefaultAetherResourceMetadata;
import org.aether.store.UpdateOptions;
import org.aether.store.fs.StoredDocument.StoredMetadata;
import org.aether.store.unique.UniqueConstraintModel;
import org.aether.store.unique.UniqueIndexTable;
import org.aether.store.unique.UniqueKey;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Filesystem JSON {@link org.aether.store.AetherResourceStore} using Gson.
 *
 * <p>Layout: {@code {root}/{resourceType}/{id}.json}. Fully synchronized on a
 * single lock for thread safety (simple and correct for prototypes; may be slow
 * under high contention).
 *
 * @param <T> domain resource type
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public final class FileSystemAetherResourceStore<T> extends AbstractAetherResourceStore<T> {
    private final Object lock = new Object();
    private final Path typeDirectory;
    private final Class<T> resourceType;
    private final Gson gson;
    private final DocumentIo documentIo;
    private final Clock clock;
    private final Supplier<String> versionGenerator;
    private final UniqueConstraintModel uniqueModel;
    private final UniqueIndexTable uniqueIndex = new UniqueIndexTable();

    /**
     * Opens a store under {@code root}/{@code type.getSimpleName()}.
     *
     * @param root storage root directory
     * @param type domain record (or Gson-compatible) class
     */
    public FileSystemAetherResourceStore(final Path root, final Class<T> type) {
        this(
                root,
                type,
                type.getSimpleName(),
                UniqueConstraintModel.forType(type),
                Clock.systemUTC(),
                () -> UUID.randomUUID().toString());
    }

    /**
     * Full constructor for tests and custom layout names.
     *
     * @param root storage root
     * @param type resource class
     * @param resourceTypeName directory name under root
     * @param uniqueModel uniqueness model
     * @param clock timestamps
     * @param versionGenerator etag supplier
     */
    public FileSystemAetherResourceStore(
            final Path root,
            final Class<T> type,
            final String resourceTypeName,
            final UniqueConstraintModel uniqueModel,
            final Clock clock,
            final Supplier<String> versionGenerator) {
        super();
        Objects.requireNonNull(root, "root");
        this.resourceType = Objects.requireNonNull(type, "type");
        this.typeDirectory = FileSystemPaths.typeDirectory(root, Objects.requireNonNull(resourceTypeName));
        this.uniqueModel = Objects.requireNonNull(uniqueModel, "uniqueModel");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.versionGenerator = Objects.requireNonNull(versionGenerator, "versionGenerator");
        this.gson = GsonSupport.createGson();
        this.documentIo = new DocumentIo(gson);
        rebuildUniqueIndex();
    }

    @Override
    protected ExceptionalResponse<AetherPersisted<T>> doCreate(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final T resource,
            final String id) {
        synchronized (lock) {
            return doCreateLocked(onError, principal, resource, id);
        }
    }

    @Override
    public ExceptionalResponse<AetherPersisted<T>> read(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final String id) {
        synchronized (lock) {
            Objects.requireNonNull(onError, "onError");
            Objects.requireNonNull(principal, "principal");
            final String idError = FileSystemPaths.invalidIdReason(id);
            if (idError != null) {
                return AetherResponses.fail(onError, AetherFailure.Validation, idError);
            }
            final Path path = FileSystemPaths.documentPath(typeDirectory, id);
            if (!documentIo.exists(path)) {
                return AetherResponses.fail(onError, AetherFailure.NotFound, "Resource not found: " + id);
            }
            try {
                return ExceptionalResponse.success(readPersisted(path));
            } catch (final IOException ex) {
                return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to read: " + id, ex);
            }
        }
    }

    @Override
    public ExceptionalResponse<AetherPersisted<T>> update(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final String id,
            final T resource,
            final String expectedVersion,
            final UpdateOptions options) {
        synchronized (lock) {
            Objects.requireNonNull(onError, "onError");
            Objects.requireNonNull(principal, "principal");
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(expectedVersion, "expectedVersion");
            Objects.requireNonNull(options, "options");
            final String idError = FileSystemPaths.invalidIdReason(id);
            if (idError != null) {
                return AetherResponses.fail(onError, AetherFailure.Validation, idError);
            }

            final Path path = FileSystemPaths.documentPath(typeDirectory, id);
            if (!documentIo.exists(path)) {
                if (options.isCreateIfAbsent()) {
                    return doCreateLocked(onError, principal, resource, id);
                }
                return AetherResponses.fail(onError, AetherFailure.NotFound, "Resource not found: " + id);
            }

            try {
                final AetherPersisted<T> existing = readPersisted(path);
                if (!existing.metadata().version().equals(expectedVersion)) {
                    return AetherResponses.fail(
                            onError,
                            AetherFailure.Conflict,
                            "Version mismatch for: " + id);
                }

                final List<UniqueKey> oldKeys = uniqueModel.keysOf(existing.resource());
                final List<UniqueKey> newKeys = uniqueModel.keysOf(resource);
                final String conflictGroup = uniqueIndex.reindex(id, oldKeys, newKeys);
                if (conflictGroup != null) {
                    return AetherResponses.fail(
                            onError,
                            AetherFailure.Conflict,
                            "Unique constraint violated for group: " + conflictGroup);
                }

                final Instant now = clock.instant();
                final AetherPersisted<T> updated = new DefaultAetherPersisted<>(
                        new DefaultAetherResourceMetadata(
                                id,
                                existing.metadata().createdAt(),
                                now,
                                versionGenerator.get(),
                                existing.metadata().createdBy(),
                                principal.name()),
                        resource);
                writePersisted(path, updated);
                return ExceptionalResponse.success(updated);
            } catch (final IOException ex) {
                return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to update: " + id, ex);
            }
        }
    }

    @Override
    public ExceptionalResponse<AetherAck> delete(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final String id) {
        synchronized (lock) {
            Objects.requireNonNull(onError, "onError");
            Objects.requireNonNull(principal, "principal");
            final String idError = FileSystemPaths.invalidIdReason(id);
            if (idError != null) {
                return AetherResponses.fail(onError, AetherFailure.Validation, idError);
            }
            final Path path = FileSystemPaths.documentPath(typeDirectory, id);
            try {
                if (documentIo.exists(path)) {
                    final AetherPersisted<T> existing = readPersisted(path);
                    documentIo.deleteIfExists(path);
                    uniqueIndex.release(id, uniqueModel.keysOf(existing.resource()));
                }
                return ExceptionalResponse.success(AetherAck.INSTANCE);
            } catch (final IOException ex) {
                return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to delete: " + id, ex);
            }
        }
    }

    private ExceptionalResponse<AetherPersisted<T>> doCreateLocked(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final T resource,
            final String id) {
        Objects.requireNonNull(onError, "onError");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(resource, "resource");
        final String idError = FileSystemPaths.invalidIdReason(id);
        if (idError != null) {
            return AetherResponses.fail(onError, AetherFailure.Validation, idError);
        }

        final Path path = FileSystemPaths.documentPath(typeDirectory, id);
        if (documentIo.exists(path)) {
            return AetherResponses.fail(onError, AetherFailure.Conflict, "Resource already exists: " + id);
        }

        final List<UniqueKey> keys = uniqueModel.keysOf(resource);
        final String conflictGroup = uniqueIndex.tryClaim(id, keys);
        if (conflictGroup != null) {
            return AetherResponses.fail(
                    onError,
                    AetherFailure.Conflict,
                    "Unique constraint violated for group: " + conflictGroup);
        }

        final Instant now = clock.instant();
        final AetherPersisted<T> created = new DefaultAetherPersisted<>(
                new DefaultAetherResourceMetadata(
                        id,
                        now,
                        now,
                        versionGenerator.get(),
                        principal.name(),
                        principal.name()),
                resource);
        try {
            writePersisted(path, created);
            return ExceptionalResponse.success(created);
        } catch (final IOException ex) {
            uniqueIndex.release(id, keys);
            return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to create: " + id, ex);
        }
    }

    private void rebuildUniqueIndex() {
        synchronized (lock) {
            if (!Files.isDirectory(typeDirectory)) {
                return;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(typeDirectory, "*.json")) {
                for (final Path path : stream) {
                    if (path.getFileName().toString().endsWith(".tmp")) {
                        continue;
                    }
                    final AetherPersisted<T> persisted = readPersisted(path);
                    uniqueIndex.tryClaim(
                            persisted.metadata().id(),
                            uniqueModel.keysOf(persisted.resource()));
                }
            } catch (final IOException ex) {
                throw new IllegalStateException("Failed to rebuild unique index under " + typeDirectory, ex);
            }
        }
    }

    private AetherPersisted<T> readPersisted(final Path path) throws IOException {
        final StoredDocument document = documentIo.read(path);
        final StoredMetadata meta = document.getMetadata();
        final T resource = gson.fromJson(document.getResource(), resourceType);
        return new DefaultAetherPersisted<>(
                new DefaultAetherResourceMetadata(
                        meta.getId(),
                        meta.getCreatedAt(),
                        meta.getUpdatedAt(),
                        meta.getVersion(),
                        meta.getCreatedBy(),
                        meta.getUpdatedBy()),
                resource);
    }

    private void writePersisted(final Path path, final AetherPersisted<T> persisted) throws IOException {
        final JsonElement resourceJson = gson.toJsonTree(persisted.resource(), resourceType);
        final StoredDocument document = new StoredDocument(
                DocumentIo.toWire(persisted.metadata()),
                resourceJson);
        documentIo.writeAtomic(path, document);
    }
}
