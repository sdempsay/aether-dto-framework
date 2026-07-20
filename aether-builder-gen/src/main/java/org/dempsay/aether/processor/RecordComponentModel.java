package org.dempsay.aether.processor;

/**
 * Metadata for a single record component used during builder generation.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class RecordComponentModel {
    private final String name;
    private final String typeName;
    private final boolean nullable;
    private final Integer minLength;
    private final Integer maxLength;
    private final String regexPattern;

    /**
     * Creates component metadata extracted from a record field and its annotations.
     *
     * @param name record component name
     * @param typeName simple or qualified type name for generated source
     * @param nullable whether {@code @Nullable} is present on the component
     * @param minLength minimum string length from {@code @MinLength}, or {@code null}
     * @param maxLength maximum string length from {@code @MaxLength}, or {@code null}
     * @param regexPattern regex pattern from {@code @RegexMatch}, or {@code null}
     */
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

    /**
     * Returns the record component name.
     *
     * @return the component identifier used in generated builder code
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the component type name for generated source.
     *
     * @return the simple or qualified type name
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * Returns whether the component allows {@code null}.
     *
     * @return {@code true} when {@code @Nullable} is present
     */
    public boolean isNullable() {
        return nullable;
    }

    /**
     * Returns the minimum string length constraint, if any.
     *
     * @return the {@code @MinLength} value, or {@code null} when absent
     */
    public Integer getMinLength() {
        return minLength;
    }

    /**
     * Returns the maximum string length constraint, if any.
     *
     * @return the {@code @MaxLength} value, or {@code null} when absent
     */
    public Integer getMaxLength() {
        return maxLength;
    }

    /**
     * Returns the regex pattern constraint, if any.
     *
     * @return the {@code @RegexMatch} pattern, or {@code null} when absent
     */
    public String getRegexPattern() {
        return regexPattern;
    }

    /**
     * Returns whether the component type is {@link String}.
     *
     * @return {@code true} for {@code String} or {@code java.lang.String}
     */
    public boolean isString() {
        return "String".equals(typeName) || "java.lang.String".equals(typeName);
    }

    /**
     * Returns whether a regex constraint is present.
     *
     * @return {@code true} when {@link #getRegexPattern()} is not {@code null}
     */
    public boolean hasRegex() {
        return regexPattern != null;
    }

    /**
     * Returns whether any string validation constraint is present.
     *
     * @return {@code true} when min length, max length, or regex constraints apply
     */
    public boolean hasStringConstraints() {
        return minLength != null || maxLength != null || hasRegex();
    }
}
