package org.aether.builder;

import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Common supertype for generated validated record builders.
 *
 * @param <T> the record type produced by {@link #build(ExceptionalListener)}
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public interface AetherBuilder<T> {

    /**
     * Validates accumulated field values and constructs the record, or reports errors.
     *
     * @param onError invoked when validation fails
     * @return success with the record, or failure after {@code onError} is invoked
     */
    ExceptionalResponse<T> build(ExceptionalListener onError);
}