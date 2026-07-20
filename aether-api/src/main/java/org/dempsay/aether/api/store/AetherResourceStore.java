package org.dempsay.aether.api.store;

import java.util.List;

import org.dempsay.aether.api.access.AetherPrincipal;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * CRUD store for a multi-instance resource type {@code T}.
 *
 * <p>Semantics parallel HTTP collection resources (POST/GET/PUT/DELETE). v1 keys
 * are always {@link String} (random UUID or preferred string id).
 *
 * <p>Parameter order: {@code onError}, {@code principal}, then operation arguments.
 *
 * @param <T> domain resource type
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface AetherResourceStore<T> {
    /**
     * POST — create a resource; the store assigns a new id (v1: random UUID string).
     *
     * @param onError failure listener; always first
     * @param principal caller identity for audit (and AAA when decorated)
     * @param resource validated domain value
     * @return success with envelope, or failure after {@code onError}
     */
    ExceptionalResponse<AetherPersisted<T>> create(
            ExceptionalListener onError,
            AetherPrincipal principal,
            T resource);

    /**
     * POST — create a resource with a preferred id.
     *
     * <p>{@code preferredId} must be non-null and non-blank. Conflict if the id
     * already exists. Do not pass null to mean auto-generate; use
     * {@link #create(ExceptionalListener, AetherPrincipal, Object)} instead.
     *
     * @param onError failure listener; always first
     * @param principal caller identity for audit (and AAA when decorated)
     * @param resource validated domain value
     * @param preferredId client- or policy-chosen id
     * @return success with envelope, or failure after {@code onError}
     */
    ExceptionalResponse<AetherPersisted<T>> create(
            ExceptionalListener onError,
            AetherPrincipal principal,
            T resource,
            String preferredId);

    /**
     * GET by id — not found if missing (also used when access is denied and
     * existence should be hidden).
     *
     * @param onError failure listener
     * @param principal caller identity
     * @param id resource id
     * @return success with envelope, or failure after {@code onError}
     */
    ExceptionalResponse<AetherPersisted<T>> read(
            ExceptionalListener onError,
            AetherPrincipal principal,
            String id);

    /**
     * GET collection — unfiltered list of all resources of this type.
     *
     * <p>Empty store succeeds with an empty list (not an error). Order is
     * <strong>by resource id</strong> (natural {@link String} order) for
     * stable enumeration. No filter, pagination, or query language (see
     * backlog for filtered query).
     *
     * <p>{@code principal} is reserved for future AAA (may filter denied rows
     * or hide existence consistently with {@link #read}).
     *
     * @param onError failure listener
     * @param principal caller identity
     * @return success with zero or more envelopes, or failure after {@code onError}
     * @since 1.0.0
     */
    ExceptionalResponse<List<AetherPersisted<T>>> list(
            ExceptionalListener onError,
            AetherPrincipal principal);

    /**
     * PUT — full replace of the resource at {@code id}.
     *
     * <p>{@code expectedVersion} is required and must match the stored version.
     * Mismatch → conflict. If missing and {@link UpdateOptions#isCreateIfAbsent()},
     * creates; otherwise not found.
     *
     * @param onError failure listener
     * @param principal caller identity
     * @param id resource id
     * @param resource new full resource body
     * @param expectedVersion version from last read
     * @param options update options; must not be null
     * @return success with new envelope, or failure after {@code onError}
     */
    ExceptionalResponse<AetherPersisted<T>> update(
            ExceptionalListener onError,
            AetherPrincipal principal,
            String id,
            T resource,
            String expectedVersion,
            UpdateOptions options);

    /**
     * DELETE — remove at id. Idempotent: missing id succeeds.
     *
     * @param onError failure listener
     * @param principal caller identity
     * @param id resource id
     * @return success with {@link AetherAck#INSTANCE}, or failure after {@code onError}
     */
    ExceptionalResponse<AetherAck> delete(
            ExceptionalListener onError,
            AetherPrincipal principal,
            String id);
}
