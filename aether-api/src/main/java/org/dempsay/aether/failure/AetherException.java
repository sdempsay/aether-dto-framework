package org.dempsay.aether.failure;

import java.util.Objects;

/**
 * Exception delivered to {@link org.dempsay.utils.exceptional.api.ExceptionalListener}
 * for Aether store and resource failures.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class AetherException extends RuntimeException {
    private final AetherFailure failure;

    /**
     * Creates an exception with a failure code and message.
     *
     * @param failure typed failure code; must not be null
     * @param message human-readable detail; must not be null
     */
    public AetherException(final AetherFailure failure, final String message) {
        super(Objects.requireNonNull(message, "message"));
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    /**
     * Creates an exception with a failure code, message, and cause.
     *
     * @param failure typed failure code; must not be null
     * @param message human-readable detail; must not be null
     * @param cause underlying cause; may be null
     */
    public AetherException(final AetherFailure failure, final String message, final Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    /**
     * Returns the typed failure code.
     *
     * @return failure code for host mapping (e.g. HTTP status)
     */
    public AetherFailure failure() {
        return failure;
    }
}
