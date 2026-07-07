package org.aether.processor;

/**
 * Metadata for a single record component used during builder generation.
 *
 * @since 0.1.0
 */
public final class RecordComponentModel {
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

    public String getName() {
        return name;
    }

    public String getTypeName() {
        return typeName;
    }

    public boolean isNullable() {
        return nullable;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public boolean isString() {
        return "String".equals(typeName) || "java.lang.String".equals(typeName);
    }

    public boolean hasRegex() {
        return regexPattern != null;
    }

    public boolean hasStringConstraints() {
        return minLength != null || maxLength != null || hasRegex();
    }
}