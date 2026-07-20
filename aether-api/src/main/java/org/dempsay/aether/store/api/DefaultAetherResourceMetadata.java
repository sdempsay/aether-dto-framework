package org.dempsay.aether.store.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable default implementation of {@link AetherResourceMetadata}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class DefaultAetherResourceMetadata implements AetherResourceMetadata {
    private final String id;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String version;
    private final String createdBy;
    private final String updatedBy;

    /**
     * Creates metadata with all fields.
     *
     * @param id resource id
     * @param createdAt creation time
     * @param updatedAt last update time
     * @param version etag token
     * @param createdBy creator principal name
     * @param updatedBy last updater principal name
     */
    public DefaultAetherResourceMetadata(
            final String id,
            final Instant createdAt,
            final Instant updatedAt,
            final String version,
            final String createdBy,
            final String updatedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = Objects.requireNonNull(version, "version");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public String createdBy() {
        return createdBy;
    }

    @Override
    public String updatedBy() {
        return updatedBy;
    }
}
