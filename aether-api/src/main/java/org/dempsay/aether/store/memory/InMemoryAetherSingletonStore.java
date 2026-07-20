package org.dempsay.aether.store.memory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.dempsay.aether.access.AetherPrincipal;
import org.dempsay.aether.failure.AetherFailure;
import org.dempsay.aether.failure.AetherResponses;
import org.dempsay.aether.store.AetherAck;
import org.dempsay.aether.store.AetherPersisted;
import org.dempsay.aether.store.AetherSingletonStore;
import org.dempsay.aether.store.DefaultAetherPersisted;
import org.dempsay.aether.store.DefaultAetherResourceMetadata;
import org.dempsay.aether.store.UpdateOptions;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * In-memory {@link AetherSingletonStore} for tests and lightweight use.
 *
 * <p><strong>Concurrency:</strong> uses {@link AtomicReference} for the single
 * slot; concurrent create/update still has check-then-act gaps similar to the
 * multi-resource in-memory store. Prefer one thread per store in unit tests.
 *
 * @param <T> domain resource type
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public class InMemoryAetherSingletonStore<T> implements AetherSingletonStore<T> {
    static final String SINGLETON_ID = "_singleton";

    private final AtomicReference<AetherPersisted<T>> holder = new AtomicReference<>();
    private final Clock clock;
    private final Supplier<String> versionGenerator;

    /**
     * Creates a store with system UTC clock and random UUID versions.
     */
    public InMemoryAetherSingletonStore() {
        this(Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    /**
     * Creates a store with injectable clock and version generator.
     *
     * @param clock wall clock
     * @param versionGenerator version etags
     */
    public InMemoryAetherSingletonStore(final Clock clock, final Supplier<String> versionGenerator) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.versionGenerator = Objects.requireNonNull(versionGenerator, "versionGenerator");
    }

    @Override
    public ExceptionalResponse<AetherPersisted<T>> create(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final T resource) {
        Objects.requireNonNull(onError, "onError");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(resource, "resource");

        final Instant now = clock.instant();
        final AetherPersisted<T> created = envelope(principal, resource, now, now, principal.name());
        if (!holder.compareAndSet(null, created)) {
            return AetherResponses.fail(
                    onError,
                    AetherFailure.Conflict,
                    "Singleton already exists");
        }
        return ExceptionalResponse.success(created);
    }

    @Override
    public ExceptionalResponse<AetherPersisted<T>> read(
            final ExceptionalListener onError,
            final AetherPrincipal principal) {
        Objects.requireNonNull(onError, "onError");
        Objects.requireNonNull(principal, "principal");
        final AetherPersisted<T> found = holder.get();
        if (found == null) {
            return AetherResponses.fail(onError, AetherFailure.NotFound, "Singleton not found");
        }
        return ExceptionalResponse.success(found);
    }

    @Override
    public ExceptionalResponse<AetherPersisted<T>> update(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final T resource,
            final String expectedVersion,
            final UpdateOptions options) {
        Objects.requireNonNull(onError, "onError");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(options, "options");

        final AetherPersisted<T> existing = holder.get();
        if (existing == null) {
            if (options.isCreateIfAbsent()) {
                return create(onError, principal, resource);
            }
            return AetherResponses.fail(onError, AetherFailure.NotFound, "Singleton not found");
        }
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
        if (!holder.compareAndSet(existing, updated)) {
            return AetherResponses.fail(onError, AetherFailure.Conflict, "Concurrent singleton modification");
        }
        return ExceptionalResponse.success(updated);
    }

    @Override
    public ExceptionalResponse<AetherAck> delete(
            final ExceptionalListener onError,
            final AetherPrincipal principal) {
        Objects.requireNonNull(onError, "onError");
        Objects.requireNonNull(principal, "principal");
        holder.set(null);
        return ExceptionalResponse.success(AetherAck.INSTANCE);
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
}
