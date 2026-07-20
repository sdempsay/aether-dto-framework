package org.dempsay.aether.api.store;

/**
 * Envelope around a domain resource and its store-owned metadata.
 *
 * @param <T> domain resource type
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface AetherPersisted<T> {
    /**
     * Returns store-owned metadata (id, timestamps, version, actors).
     *
     * @return metadata
     */
    AetherResourceMetadata metadata();

    /**
     * Returns the pure domain resource value.
     *
     * @return resource
     */
    T resource();
}
