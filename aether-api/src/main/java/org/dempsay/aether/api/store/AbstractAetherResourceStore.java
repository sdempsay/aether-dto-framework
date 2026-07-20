package org.dempsay.aether.api.store;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.dempsay.aether.api.access.AetherPrincipal;
import org.dempsay.aether.api.failure.AetherFailure;
import org.dempsay.aether.api.failure.AetherResponses;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Optional skeletal {@link AetherResourceStore} that implements create overloads
 * and delegates insert to {@link #doCreate}.
 *
 * <p>Providers may extend this class or implement {@link AetherResourceStore}
 * directly.
 *
 * @param <T> domain resource type
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public abstract class AbstractAetherResourceStore<T> implements AetherResourceStore<T> {
    private final Supplier<String> idGenerator;

    /**
     * Creates a store with the default UUID string id generator.
     */
    protected AbstractAetherResourceStore() {
        this(AbstractAetherResourceStore::newUuid);
    }

    /**
     * Creates a store with a custom id generator (useful in tests).
     *
     * @param idGenerator supplier of new ids; must not be null
     */
    protected AbstractAetherResourceStore(final Supplier<String> idGenerator) {
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    @Override
    public final ExceptionalResponse<AetherPersisted<T>> create(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final T resource) {
        Objects.requireNonNull(onError, "onError");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(resource, "resource");
        return doCreate(onError, principal, resource, idGenerator.get());
    }

    @Override
    public final ExceptionalResponse<AetherPersisted<T>> create(
            final ExceptionalListener onError,
            final AetherPrincipal principal,
            final T resource,
            final String preferredId) {
        Objects.requireNonNull(onError, "onError");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(resource, "resource");
        if (preferredId == null || preferredId.isBlank()) {
            return AetherResponses.fail(
                    onError,
                    AetherFailure.Validation,
                    "preferredId must not be null or blank");
        }
        return doCreate(onError, principal, resource, preferredId.trim());
    }

    /**
     * Inserts a new resource under the given id.
     *
     * <p>Implementations must fail with {@link AetherFailure#Conflict} if the id
     * already exists.
     *
     * @param onError failure listener
     * @param principal caller
     * @param resource domain value
     * @param id assigned or preferred id (never null/blank)
     * @return success envelope or failure
     */
    protected abstract ExceptionalResponse<AetherPersisted<T>> doCreate(
            ExceptionalListener onError,
            AetherPrincipal principal,
            T resource,
            String id);

    private static String newUuid() {
        return UUID.randomUUID().toString();
    }
}
