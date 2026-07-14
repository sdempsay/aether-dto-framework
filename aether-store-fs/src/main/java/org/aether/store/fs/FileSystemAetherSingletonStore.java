package org.aether.store.fs;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.aether.access.AetherPrincipal;
import org.aether.failure.AetherFailure;
import org.aether.failure.AetherResponses;
import org.aether.store.AetherAck;
import org.aether.store.AetherPersisted;
import org.aether.store.AetherSingletonStore;
import org.aether.store.DefaultAetherPersisted;
import org.aether.store.DefaultAetherResourceMetadata;
import org.aether.store.UpdateOptions;
import org.aether.store.fs.StoredDocument.StoredMetadata;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Filesystem JSON {@link AetherSingletonStore} using Gson.
 *
 * <p>Layout: {@code {root}/{resourceType}/_singleton.json}. Fully synchronized.
 * All I/O uses exceptional patterns.
 *
 * @param <T> domain resource type
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public final class FileSystemAetherSingletonStore<T> implements AetherSingletonStore<T> {
    static final String SINGLETON_ID = "_singleton";

    private final Object lock = new Object();
    private final Path documentPath;
    private final Class<T> resourceType;
    private final Gson gson;
    private final DocumentIo documentIo;
    private final Clock clock;
    private final Supplier<String> versionGenerator;

    /**
     * Opens a singleton store under {@code root}/{@code type.getSimpleName()}/_singleton.json}.
     *
     * @param root storage root
     * @param type domain type
     */
    public FileSystemAetherSingletonStore(final Path root, final Class<T> type) {
        this(root, type, type.getSimpleName(), Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    /**
     * Full constructor.
     *
     * @param root storage root
     * @param type domain type
     * @param resourceTypeName directory name
     * @param clock timestamps
     * @param versionGenerator etags
     */
    public FileSystemAetherSingletonStore(
            final Path root,
            final Class<T> type,
            final String resourceTypeName,
            final Clock clock,
            final Supplier<String> versionGenerator) {
        Objects.requireNonNull(root, "root");
        this.resourceType = Objects.requireNonNull(type, "type");
        this.documentPath = FileSystemPaths.singletonPath(
                FileSystemPaths.typeDirectory(root, Objects.requireNonNull(resourceTypeName)));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.versionGenerator = Objects.requireNonNull(versionGenerator, "versionGenerator");
        this.gson = GsonSupport.createGson();
        this.documentIo = new DocumentIo(gson);
    }

    @Override
    public ExceptionalResponse<AetherPersisted<T>> create(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final T resource) {
        synchronized (lock) {
            Objects.requireNonNull(onError, "onError");
            Objects.requireNonNull(principal, "principal");
            Objects.requireNonNull(resource, "resource");
            if (documentIo.exists(documentPath)) {
                return AetherResponses.fail(onError, AetherFailure.Conflict, "Singleton already exists");
            }
            final Instant now = clock.instant();
            final AetherPersisted<T> created = envelope(principal, resource, now, now, principal.name());
            final ExceptionalResponse<AetherAck> written = writePersisted(created);
            if (written.wasError()) {
                return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to create singleton");
            }
            return ExceptionalResponse.success(created);
        }
    }

    @Override
    public ExceptionalResponse<AetherPersisted<T>> read(
            final ExceptionalListener onError,
            final AetherPrincipal principal) {
        synchronized (lock) {
            Objects.requireNonNull(onError, "onError");
            Objects.requireNonNull(principal, "principal");
            if (!documentIo.exists(documentPath)) {
                return AetherResponses.fail(onError, AetherFailure.NotFound, "Singleton not found");
            }
            return readPersisted(onError);
        }
    }

    @Override
    public ExceptionalResponse<AetherPersisted<T>> update(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final T resource,
            final String expectedVersion,
            final UpdateOptions options) {
        synchronized (lock) {
            Objects.requireNonNull(onError, "onError");
            Objects.requireNonNull(principal, "principal");
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(expectedVersion, "expectedVersion");
            Objects.requireNonNull(options, "options");

            if (!documentIo.exists(documentPath)) {
                if (options.isCreateIfAbsent()) {
                    return create(onError, principal, resource);
                }
                return AetherResponses.fail(onError, AetherFailure.NotFound, "Singleton not found");
            }

            final ExceptionalResponse<AetherPersisted<T>> existingResponse = readPersisted(onError);
            if (existingResponse.wasError()) {
                return existingResponse;
            }
            final AetherPersisted<T> existing = existingResponse.response();
            if (!existing.metadata().version().equals(expectedVersion)) {
                return AetherResponses.fail(onError, AetherFailure.Conflict, "Version mismatch for singleton");
            }
            final Instant now = clock.instant();
            final AetherPersisted<T> updated = envelope(
                    principal,
                    resource,
                    existing.metadata().createdAt(),
                    now,
                    existing.metadata().createdBy());
            final ExceptionalResponse<AetherAck> written = writePersisted(updated);
            if (written.wasError()) {
                return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to update singleton");
            }
            return ExceptionalResponse.success(updated);
        }
    }

    @Override
    public ExceptionalResponse<AetherAck> delete(
            final ExceptionalListener onError,
            final AetherPrincipal principal) {
        synchronized (lock) {
            Objects.requireNonNull(onError, "onError");
            Objects.requireNonNull(principal, "principal");
            final ExceptionalResponse<AetherAck> deleted = documentIo.deleteIfExists(documentPath);
            if (deleted.wasError()) {
                return AetherResponses.fail(onError, AetherFailure.Internal, "Failed to delete singleton");
            }
            return ExceptionalResponse.success(AetherAck.INSTANCE);
        }
    }

    private AetherPersisted<T> envelope(
            final AetherPrincipal principal,
            final T resource,
            final Instant createdAt,
            final Instant updatedAt,
            final String createdBy) {
        return new DefaultAetherPersisted<>(
                new DefaultAetherResourceMetadata(
                        SINGLETON_ID,
                        createdAt,
                        updatedAt,
                        versionGenerator.get(),
                        createdBy,
                        principal.name()),
                resource);
    }

    private ExceptionalResponse<AetherPersisted<T>> readPersisted(final ExceptionalListener onError) {
        final ExceptionalResponse<AetherPersisted<T>> read = documentIo.read(documentPath).chain(
                (listener, document) -> {
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
        if (read.wasError()) {
            return AetherResponses.fail(onError, AetherFailure.NotFound, "Failed to read singleton");
        }
        return read;
    }

    private ExceptionalResponse<AetherAck> writePersisted(final AetherPersisted<T> persisted) {
        final JsonElement resourceJson = gson.toJsonTree(persisted.resource(), resourceType);
        return documentIo.writeAtomic(
                documentPath,
                new StoredDocument(DocumentIo.toWire(persisted.metadata()), resourceJson));
    }
}
