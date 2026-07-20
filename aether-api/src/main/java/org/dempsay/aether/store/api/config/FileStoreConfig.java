package org.dempsay.aether.store.api.config;

/**
 * Configuration for filesystem-backed Aether stores.
 *
 * <p>Publish an OSGi service implementing this interface (for example from Config
 * Admin / metatype). Generated FS provider adapters with {@code scr = true} take
 * it via {@code @Reference} and use {@link #location()} as the storage root path.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface FileStoreConfig {
    /**
     * Default relative location when no configuration is supplied.
     */
    String DEFAULT_LOCATION = "aetherFileStore";

    /**
     * Filesystem root for store documents (absolute or relative path string).
     *
     * @return storage root; never {@code null}
     */
    default String location() {
        return DEFAULT_LOCATION;
    }
}
