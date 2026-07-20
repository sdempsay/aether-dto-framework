package org.dempsay.aether.failure;

/**
 * Typed persistence / resource failure codes for the exceptional listener path.
 *
 * <p>Each constant carries an HTTP status {@code int} for host-edge mapping
 * (no servlet types in this module). Enum constants use PascalCase.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public enum AetherFailure {
    /** Domain or request validation problems (~400). */
    Validation(400),

    /**
     * Missing resource (~404). Also used when unauthorized read/update/delete
     * should hide existence.
     */
    NotFound(404),

    /**
     * Duplicate id, unique violation, version mismatch, or singleton already
     * exists (~409).
     */
    Conflict(409),

    /** Path id vs body identity mismatch (~400). */
    Identity(400),

    /**
     * Explicit access deny (~403), e.g. create when the type is not allowed.
     * Prefer {@link #NotFound} on read paths when hiding existence.
     */
    Forbidden(403),

    /** Backend I/O or unexpected failure (~500). */
    Internal(500);

    private final int httpStatus;

    AetherFailure(final int httpStatus) {
        this.httpStatus = httpStatus;
    }

    /**
     * Returns the suggested HTTP status code for this failure.
     *
     * @return status code such as {@code 404}
     */
    public int httpStatus() {
        return httpStatus;
    }
}
