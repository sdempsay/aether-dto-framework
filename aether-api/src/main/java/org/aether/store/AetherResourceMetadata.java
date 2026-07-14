package org.aether.store;

import java.time.Instant;

/**
 * Store-owned metadata for a persisted resource.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public interface AetherResourceMetadata {
    /**
     * Returns the resource id (UUID string or preferred string key).
     *
     * @return non-null id
     */
    String id();

    /**
     * Returns when the resource was first created.
     *
     * @return creation timestamp
     */
    Instant createdAt();

    /**
     * Returns when the resource was last updated.
     *
     * @return last update timestamp
     */
    Instant updatedAt();

    /**
     * Returns the opaque version / etag (random UUID string per successful write).
     *
     * @return non-null version token
     */
    String version();

    /**
     * Returns the principal name that created the resource.
     *
     * @return creator identity string
     */
    String createdBy();

    /**
     * Returns the principal name that last updated the resource.
     *
     * @return last updater identity string
     */
    String updatedBy();
}
