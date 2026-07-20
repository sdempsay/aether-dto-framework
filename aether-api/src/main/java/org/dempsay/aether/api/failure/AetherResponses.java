package org.dempsay.aether.api.failure;

import java.util.Objects;

import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Helpers for producing {@link ExceptionalResponse} failures with
 * {@link AetherException} on the listener path.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class AetherResponses {
    private AetherResponses() {
    }

    /**
     * Notifies {@code onError} with an {@link AetherException} and returns a
     * failure response.
     *
     * @param <T> response payload type
     * @param onError listener; must not be null
     * @param failure typed failure; must not be null
     * @param message detail message; must not be null
     * @return {@link ExceptionalResponse#failure()}
     */
    public static <T> ExceptionalResponse<T> fail(
            final ExceptionalListener onError,
            final AetherFailure failure,
            final String message) {
        Objects.requireNonNull(onError, "onError");
        onError.accept(new AetherException(failure, message));
        return ExceptionalResponse.failure();
    }

    /**
     * Notifies {@code onError} with an {@link AetherException} including a cause
     * and returns a failure response.
     *
     * @param <T> response payload type
     * @param onError listener; must not be null
     * @param failure typed failure; must not be null
     * @param message detail message; must not be null
     * @param cause underlying cause; may be null
     * @return {@link ExceptionalResponse#failure()}
     */
    public static <T> ExceptionalResponse<T> fail(
            final ExceptionalListener onError,
            final AetherFailure failure,
            final String message,
            final Throwable cause) {
        Objects.requireNonNull(onError, "onError");
        onError.accept(new AetherException(failure, message, cause));
        return ExceptionalResponse.failure();
    }
}
