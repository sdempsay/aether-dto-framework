package org.aether.processor;

/**
 * Metadata for a single record component used during builder generation.
 *
 * @since 0.1.0
 */
final class RecordComponentModel {
    private final String name;
    private final String typeName;
    private final boolean nullable;
    private final Integer minLength;
    private final Integer maxLength;
    private final String regexPattern;

    RecordComponentModel(
            final String name,
            final String typeName,
            final boolean nullable,
            final Integer minLength,
            final Integer maxLength,
            final String regexPattern) {
        this.name = name;
        this.typeName = typeName;
        this.nullable = nullable;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.regexPattern = regexPattern;
    }

    String name() {
        return name;
    }

    String typeName() {
        return typeName;
    }

    boolean nullable() {
        return nullable;
    }

    Integer minLength() {
        return minLength;
    }

    Integer maxLength() {
        return maxLength;
    }

    String regexPattern() {
        return regexPattern;
    }

    boolean isString() {
        return "String".equals(typeName) || "java.lang.String".equals(typeName);
    }

    boolean hasRegex() {
        return regexPattern != null;
    }
}