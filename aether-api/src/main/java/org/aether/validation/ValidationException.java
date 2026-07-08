package org.aether.validation;

import java.util.List;

/**
 * Aggregated validation failure from a generated builder.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public final class ValidationException extends RuntimeException {
    private final List<String> errors;

    /**
     * Creates an exception describing one or more field validation failures.
     *
     * @param errors human-readable validation messages; must not be empty
     */
    public ValidationException(final List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * Returns the individual validation error messages.
     *
     * @return an immutable list of field validation failures
     */
    public List<String> getErrors() {
        return errors;
    }
}
