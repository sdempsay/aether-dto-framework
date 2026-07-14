package org.aether.store.memory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.aether.access.AetherPrincipal;
import org.aether.failure.AetherFailure;
import org.aether.failure.AetherResponses;
import org.aether.store.AbstractAetherResourceStore;
import org.aether.store.AetherAck;
import org.aether.store.AetherPersisted;
import org.aether.store.AetherResourceStore;
import org.aether.store.DefaultAetherPersisted;
import org.aether.store.DefaultAetherResourceMetadata;
import org.aether.store.UpdateOptions;
import org.aether.store.unique.UniqueConstraintModel;
import org.aether.store.unique.UniqueIndexTable;
import org.aether.store.unique.UniqueKey;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * In-memory {@link AetherResourceStore} for tests and lightweight use.
 *
 * <p>Does not require disk or a database — supports the project goal of
 * unit-testing applications against store ports without infrastructure.
 * When constructed with a record {@link Class}, enforces {@code @Unique}
 * constraints.
 *
 * <p><strong>Concurrency (not a production multi-writer store):</strong> this
 * implementation is aimed at unit tests. Individual {@link ConcurrentHashMap}
 * operations are thread-safe, but create/update/delete each perform
 * <em>multiple</em> steps (unique index claim/release/reindex, then entry
 * put/replace/remove). Those sequences are not atomic end-to-end. Concurrent
 * writers on the same id (or racing unique-key reindex) can leave the unique
 * index and document map inconsistent, or lose updates. Prefer single-threaded
 * use per store instance in tests; add per-id locking only if a suite truly
 * needs parallel writers.
 *
 * @param <T> domain resource type
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public final class InMemoryAetherResourceStore<T> extends AbstractAetherResourceStore<T> {
    private final ConcurrentMap<String, AetherPersisted<T>> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Supplier<String> versionGenerator;
    private final UniqueConstraintModel uniqueModel;
    private final UniqueIndexTable uniqueIndex = new UniqueIndexTable();

    /**
     * Creates a store with no uniqueness model (suitable for non-record tests).
     */
    public InMemoryAetherResourceStore() {
        this(UniqueConstraintModel.empty(), Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    /**
     * Creates a store that discovers {@code @Unique} on a record type.
     *
     * @param type domain record class
     */
    public InMemoryAetherResourceStore(final Class<T> type) {
        this(UniqueConstraintModel.forType(type), Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    /**
     * Creates a store with injectable clock and version generator (tests).
     *
     * @param clock wall clock for timestamps
     * @param versionGenerator supplier of version etags
     */
    public InMemoryAetherResourceStore(final Clock clock, final Supplier<String> versionGenerator) {
        this(UniqueConstraintModel.empty(), clock, versionGenerator);
    }

    /**
     * Creates a store with custom id generation, clock, and version generator.
     *
     * @param idGenerator new resource ids
     * @param clock wall clock
     * @param versionGenerator version etags
     */
    public InMemoryAetherResourceStore(
            final Supplier<String> idGenerator,
            final Clock clock,
            final Supplier<String> versionGenerator) {
        this(UniqueConstraintModel.empty(), idGenerator, clock, versionGenerator);
    }

    /**
     * Creates a store with an explicit unique model and defaults for id/clock/version.
     *
     * @param uniqueModel uniqueness model
     * @param clock wall clock
     * @param versionGenerator version etags
     */
    public InMemoryAetherResourceStore(
            final UniqueConstraintModel uniqueModel,
            final Clock clock,
            final Supplier<String> versionGenerator) {
        this(uniqueModel, () -> UUID.randomUUID().toString(), clock, versionGenerator);
    }

    /**
     * Full constructor.
     *
     * @param uniqueModel uniqueness model
     * @param idGenerator new resource ids
     * @param clock wall clock
     * @param versionGenerator version etags
     */
    public InMemoryAetherResourceStore(
            final UniqueConstraintModel uniqueModel,
            final Supplier<String> idGenerator,
            final Clock clock,
            final Supplier<String> versionGenerator) {
        super(idGenerator);
        this.uniqueModel = Objects.requireNonNull(uniqueModel, "uniqueModel");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.versionGenerator = Objects.requireNonNull(versionGenerator, "versionGenerator");
    }

    @Override
    protected ExceptionalResponse<AetherPersisted<T>> doCreate(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final T resource,
            final String id) {
        final List<UniqueKey> keys = uniqueModel.keysOf(resource);
        final String conflictGroup = uniqueIndex.tryClaim(id, keys);
        if (conflictGroup != null) {
            return AetherResponses.fail(
                    onError,
                    AetherFailure.Conflict,
                    "Unique constraint violated for group: " + conflictGroup);
        }

        final Instant now = clock.instant();
        final String version = versionGenerator.get();
        final AetherPersisted<T> created = new DefaultAetherPersisted<>(
                new DefaultAetherResourceMetadata(
                        id,
                        now,
                        now,
                        version,
                        principal.name(),
                        principal.name()),
                resource);
        final AetherPersisted<T> previous = entries.putIfAbsent(id, created);
        if (previous != null) {
            uniqueIndex.release(id, keys);
            return AetherResponses.fail(onError, AetherFailure.Conflict, "Resource already exists: " + id);
        }
        return ExceptionalResponse.success(created);
    }

    @Override
    public ExceptionalResponse<AetherPersisted<T>> read(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final String id) {
        Objects.requireNonNull(onError, "onError");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(id, "id");
        final AetherPersisted<T> found = entries.get(id);
        if (found == null) {
            return AetherResponses.fail(onError, AetherFailure.NotFound, "Resource not found: " + id);
        }
        return ExceptionalResponse.success(found);
    }

    @Override
    public ExceptionalResponse<AetherPersisted<T>> update(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final String id,
            final T resource,
            final String expectedVersion,
            final UpdateOptions options) {
        Objects.requireNonNull(onError, "onError");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(options, "options");

        final AetherPersisted<T> existing = entries.get(id);
        if (existing == null) {
            if (options.isCreateIfAbsent()) {
                return doCreate(onError, principal, resource, id);
            }
            return AetherResponses.fail(onError, AetherFailure.NotFound, "Resource not found: " + id);
        }
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

        final boolean replaced = entries.replace(id, existing, updated);
        if (!replaced) {
            uniqueIndex.reindex(id, newKeys, oldKeys);
            return AetherResponses.fail(
                    onError,
                    AetherFailure.Conflict,
                    "Concurrent modification for: " + id);
        }
        return ExceptionalResponse.success(updated);
    }

    @Override
    public ExceptionalResponse<AetherAck> delete(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final String id) {
        Objects.requireNonNull(onError, "onError");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(id, "id");
        final AetherPersisted<T> removed = entries.remove(id);
        if (removed != null) {
            uniqueIndex.release(id, uniqueModel.keysOf(removed.resource()));
        }
        return ExceptionalResponse.success(AetherAck.INSTANCE);
    }
}
