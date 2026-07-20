package org.dempsay.aether.store.fs;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.dempsay.aether.access.AetherPrincipal;
import org.dempsay.aether.failure.AetherFailure;
import org.dempsay.aether.failure.AetherResponses;
import org.dempsay.aether.store.AbstractAetherResourceStore;
import org.dempsay.aether.store.AetherAck;
import org.dempsay.aether.store.AetherPersisted;
import org.dempsay.aether.store.DefaultAetherPersisted;
import org.dempsay.aether.store.DefaultAetherResourceMetadata;
import org.dempsay.aether.store.UpdateOptions;
import org.dempsay.aether.store.fs.StoredDocument.StoredMetadata;
import org.dempsay.aether.store.unique.UniqueConstraintModel;
import org.dempsay.aether.store.unique.UniqueIndexTable;
import org.dempsay.aether.store.unique.UniqueKey;
import org.dempsay.aether.failure.AetherException;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResource;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Filesystem JSON {@link org.dempsay.aether.store.AetherResourceStore} using Gson.
 *
 * <p>Layout: {@code {root}/{resourceType}/{id}.json}. Fully synchronized on a
 * single lock for thread safety. All I/O uses exceptional patterns (no
 * {@code throws} on store methods).
 *
 * @param <T> domain resource type
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
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
    private boolean uniqueIndexLoaded;

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
        this.uniqueIndexLoaded = false;
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
            final ExceptionalResponse<AetherAck> indexReady = ensureUniqueIndex(onError);
            if (indexReady.wasError()) {
                return ExceptionalResponse.failure();
            }
            final String idError = FileSystemPaths.invalidIdReason(id);
            if (idError != null) {
                return AetherResponses.fail(onError, AetherFailure.Validation, idError);
            }
            final Path path = FileSystemPaths.documentPath(typeDirectory, id);
            if (!documentIo.exists(path)) {
                return AetherResponses.fail(onError, AetherFailure.NotFound, "Resource not found: " + id);
            }
            return readPersisted(onError, path);
        }
    }

    @Override
    public ExceptionalResponse<List<AetherPersisted<T>>> list(
            final ExceptionalListener onError,
            final AetherPrincipal principal) {
        synchronized (lock) {
            Objects.requireNonNull(onError, "onError");
            Objects.requireNonNull(principal, "principal");
            final ExceptionalResponse<AetherAck> indexReady = ensureUniqueIndex(onError);
            if (indexReady.wasError()) {
                return ExceptionalResponse.failure();
            }
            if (!Files.isDirectory(typeDirectory)) {
                return ExceptionalResponse.success(List.of());
            }
            return ExceptionalResource.<DirectoryStream<Path>, List<AetherPersisted<T>>>of(
                    () -> Files.newDirectoryStream(typeDirectory, "*.json"),
                    stream -> {
                        final List<AetherPersisted<T>> collected = new ArrayList<>();
                        for (final Path path : stream) {
                            final ExceptionalResponse<AetherPersisted<T>> persisted = readPersistedQuiet(path);
                            if (persisted.wasError()) {
                                throw new AetherException(
                                        AetherFailure.Internal,
                                        "Failed to read document during list: " + path.getFileName());
                            }
                            collected.add(persisted.response());
                        }
                        collected.sort(Comparator.comparing(p -> p.metadata().id()));
                        return List.copyOf(collected);
                    }).with(onError).execute();
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
            final ExceptionalResponse<AetherAck> indexReady = ensureUniqueIndex(onError);
            if (indexReady.wasError()) {
                return ExceptionalResponse.failure();
            }
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

            final ExceptionalResponse<AetherPersisted<T>> existingResponse = readPersisted(onError, path);
            if (existingResponse.wasError()) {
                return existingResponse;
            }
            final AetherPersisted<T> existing = existingResponse.response();
            if (!existing.metadata().version().equals(expectedVersion)) {
                return AetherResponses.fail(
                        onError,
                        AetherFailure.Conflict,
                        "Version mismatch for: " + id);
            }

            final ExceptionalResponse<List<UniqueKey>> oldKeysResponse = uniqueModel.keysOf(existing.resource());
            if (oldKeysResponse.wasError()) {
                return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to read unique keys");
            }
            final ExceptionalResponse<List<UniqueKey>> newKeysResponse = uniqueModel.keysOf(resource);
            if (newKeysResponse.wasError()) {
                return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to read unique keys");
            }
            final String conflictGroup = uniqueIndex.reindex(
                    id,
                    oldKeysResponse.response(),
                    newKeysResponse.response());
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
            final ExceptionalResponse<AetherAck> written = writePersisted(path, updated);
            if (written.wasError()) {
                uniqueIndex.reindex(id, newKeysResponse.response(), oldKeysResponse.response());
                return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to update: " + id);
            }
            return ExceptionalResponse.success(updated);
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
            final ExceptionalResponse<AetherAck> indexReady = ensureUniqueIndex(onError);
            if (indexReady.wasError()) {
                return indexReady;
            }
            final String idError = FileSystemPaths.invalidIdReason(id);
            if (idError != null) {
                return AetherResponses.fail(onError, AetherFailure.Validation, idError);
            }
            final Path path = FileSystemPaths.documentPath(typeDirectory, id);
            if (!documentIo.exists(path)) {
                return ExceptionalResponse.success(AetherAck.INSTANCE);
            }
            final ExceptionalResponse<AetherPersisted<T>> existingResponse = readPersisted(onError, path);
            if (existingResponse.wasError()) {
                return ExceptionalResponse.failure();
            }
            final ExceptionalResponse<List<UniqueKey>> keysResponse =
                    uniqueModel.keysOf(existingResponse.response().resource());
            if (keysResponse.wasError()) {
                return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to read unique keys");
            }
            final ExceptionalResponse<AetherAck> deleted = documentIo.deleteIfExists(path);
            if (deleted.wasError()) {
                return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to delete: " + id);
            }
            uniqueIndex.release(id, keysResponse.response());
            return ExceptionalResponse.success(AetherAck.INSTANCE);
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
        final ExceptionalResponse<AetherAck> indexReady = ensureUniqueIndex(onError);
        if (indexReady.wasError()) {
            return ExceptionalResponse.failure();
        }
        final String idError = FileSystemPaths.invalidIdReason(id);
        if (idError != null) {
            return AetherResponses.fail(onError, AetherFailure.Validation, idError);
        }

        final Path path = FileSystemPaths.documentPath(typeDirectory, id);
        if (documentIo.exists(path)) {
            return AetherResponses.fail(onError, AetherFailure.Conflict, "Resource already exists: " + id);
        }

        final ExceptionalResponse<List<UniqueKey>> keysResponse = uniqueModel.keysOf(resource);
        if (keysResponse.wasError()) {
            return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to read unique keys");
        }
        final List<UniqueKey> keys = keysResponse.response();
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
        final ExceptionalResponse<AetherAck> written = writePersisted(path, created);
        if (written.wasError()) {
            uniqueIndex.release(id, keys);
            return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to create: " + id);
        }
        return ExceptionalResponse.success(created);
    }

    private ExceptionalResponse<AetherAck> ensureUniqueIndex(final ExceptionalListener onError) {
        if (uniqueIndexLoaded) {
            return ExceptionalResponse.success(AetherAck.INSTANCE);
        }
        final ExceptionalResponse<AetherAck> rebuilt = rebuildUniqueIndex(onError);
        if (rebuilt.wasError()) {
            // Listener already received AetherException from rebuild.
            return ExceptionalResponse.failure();
        }
        uniqueIndexLoaded = true;
        return ExceptionalResponse.success(AetherAck.INSTANCE);
    }

    private ExceptionalResponse<AetherAck> rebuildUniqueIndex(final ExceptionalListener onError) {
        if (!Files.isDirectory(typeDirectory)) {
            return ExceptionalResponse.success(AetherAck.INSTANCE);
        }
        return ExceptionalResource.<DirectoryStream<Path>, AetherAck>of(
                () -> Files.newDirectoryStream(typeDirectory, "*.json"),
                stream -> {
                    for (final Path path : stream) {
                        final ExceptionalResponse<AetherPersisted<T>> persisted = readPersistedQuiet(path);
                        if (persisted.wasError()) {
                            throw new AetherException(
                                    AetherFailure.NotFound,
                                    "Failed to read document during index rebuild: " + path.getFileName());
                        }
                        final ExceptionalResponse<List<UniqueKey>> keys =
                                uniqueModel.keysOf(persisted.response().resource());
                        if (keys.wasError()) {
                            throw new AetherException(
                                    AetherFailure.Internal,
                                    "Failed to extract unique keys from: " + path.getFileName());
                        }
                        uniqueIndex.tryClaim(persisted.response().metadata().id(), keys.response());
                    }
                    return AetherAck.INSTANCE;
                }).with(onError).execute();
    }

    private ExceptionalResponse<AetherPersisted<T>> readPersisted(
            final ExceptionalListener onError,
            final Path path) {
        final ExceptionalResponse<AetherPersisted<T>> read = readPersistedQuiet(path);
        if (read.wasError()) {
            return AetherResponses.fail(
                    onError,
                    AetherFailure.NotFound,
                    "Failed to read: " + path.getFileName());
        }
        return read;
    }

    private ExceptionalResponse<AetherPersisted<T>> readPersistedQuiet(final Path path) {
        return documentIo.read(path).chain((listener, document) -> {
            final StoredMetadata meta = document.getMetadata();
            final T resource = gson.fromJson(document.getResource(), resourceType);
            return ExceptionalResponse.success(new DefaultAetherPersisted<>(
                    new DefaultAetherResourceMetadata(
                            meta.getId(),
                            meta.getCreatedAt(),
                            meta.getUpdatedAt(),
                            meta.getVersion(),
                            meta.getCreatedBy(),
                            meta.getUpdatedBy()),
                    resource));
        });
    }

    private ExceptionalResponse<AetherAck> writePersisted(
            final Path path,
            final AetherPersisted<T> persisted) {
        final JsonElement resourceJson = gson.toJsonTree(persisted.resource(), resourceType);
        final StoredDocument document = new StoredDocument(
                DocumentIo.toWire(persisted.metadata()),
                resourceJson);
        return documentIo.writeAtomic(path, document);
    }
}
