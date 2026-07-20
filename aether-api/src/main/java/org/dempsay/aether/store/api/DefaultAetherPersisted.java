package org.dempsay.aether.store.api;

import java.util.Objects;

/**
 * Immutable default implementation of {@link AetherPersisted}.
 *
 * @param <T> domain resource type
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class DefaultAetherPersisted<T> implements AetherPersisted<T> {
    private final AetherResourceMetadata metadata;
    private final T resource;

    /**
     * Creates an envelope.
     *
     * @param metadata store metadata
     * @param resource domain value
     */
    public DefaultAetherPersisted(final AetherResourceMetadata metadata, final T resource) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.resource = Objects.requireNonNull(resource, "resource");
    }

    @Override
    public AetherResourceMetadata metadata() {
        return metadata;
    }

    @Override
    public T resource() {
        return resource;
    }
}
