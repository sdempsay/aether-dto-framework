package org.dempsay.aether.store;

import org.dempsay.aether.access.AetherPrincipal;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * CRUD store for a type with at most one instance (no caller-facing id).
 *
 * <p>HTTP analogy: a single resource URL (e.g. {@code /config}), not a collection.
 *
 * @param <T> domain resource type
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface AetherSingletonStore<T> {
    /**
     * POST — create the only instance; conflict if one already exists.
     *
     * @param onError failure listener
     * @param principal caller identity
     * @param resource domain value
     * @return success with envelope, or failure after {@code onError}
     */
    ExceptionalResponse<AetherPersisted<T>> create(
            ExceptionalListener onError,
            AetherPrincipal principal,
            T resource);

    /**
     * GET — read the instance; not found if absent.
     *
     * @param onError failure listener
     * @param principal caller identity
     * @return success with envelope, or failure after {@code onError}
     */
    ExceptionalResponse<AetherPersisted<T>> read(
            ExceptionalListener onError,
            AetherPrincipal principal);

    /**
     * PUT — full replace; required expected version; optional create-if-absent.
     *
     * @param onError failure listener
     * @param principal caller identity
     * @param resource new full body
     * @param expectedVersion version from last read
     * @param options update options
     * @return success with envelope, or failure after {@code onError}
     */
    ExceptionalResponse<AetherPersisted<T>> update(
            ExceptionalListener onError,
            AetherPrincipal principal,
            T resource,
            String expectedVersion,
            UpdateOptions options);

    /**
     * DELETE — remove if present; idempotent if already absent.
     *
     * @param onError failure listener
     * @param principal caller identity
     * @return success with {@link AetherAck#INSTANCE}
     */
    ExceptionalResponse<AetherAck> delete(
            ExceptionalListener onError,
            AetherPrincipal principal);
}
