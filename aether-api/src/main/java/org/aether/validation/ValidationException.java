package org.aether.validation;

import java.util.List;

/**
 * Aggregated validation failure from a generated builder.
 *
 * @since 0.1.0
 */
public final class ValidationException extends RuntimeException {
    private final List<String> errors;

    public ValidationException(final List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}