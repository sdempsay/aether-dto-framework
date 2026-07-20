package org.dempsay.aether.store.api;

/**
 * Options for resource update (PUT) operations.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class UpdateOptions {
    private static final UpdateOptions DEFAULTS = new UpdateOptions(false);
    private static final UpdateOptions CREATE_IF_ABSENT = new UpdateOptions(true);

    private final boolean createIfAbsent;

    private UpdateOptions(final boolean createIfAbsent) {
        this.createIfAbsent = createIfAbsent;
    }

    /**
     * Returns options with {@code createIfAbsent == false} (update requires an
     * existing resource).
     *
     * @return default options
     */
    public static UpdateOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns options that create the resource when missing (upsert-style update).
     *
     * @return create-if-absent options
     */
    public static UpdateOptions createIfAbsent() {
        return CREATE_IF_ABSENT;
    }

    /**
     * Returns whether a missing resource should be created on update.
     *
     * @return true if update may create
     */
    public boolean isCreateIfAbsent() {
        return createIfAbsent;
    }
}
