package org.aether.processor;

import java.util.List;

/**
 * Emits generated builder source for a validated record DTO.
 *
 * @since 0.1.0
 */
final class BuilderCodegen {
    private BuilderCodegen() {
    }

    static String generate(
            final String packageName,
            final String recordSimpleName,
            final List<RecordComponentModel> components) {
        final String builderName = recordSimpleName + "Builder";
        final boolean needsPattern = components.stream().anyMatch(RecordComponentModel::hasRegex);

        final StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        source.append("import java.util.ArrayList;\n");
        if (needsPattern) {
            source.append("import java.util.regex.Pattern;\n");
        }
        source.append("import org.aether.validation.ValidationException;\n");
        source.append("import org.dempsay.utils.exceptional.api.ExceptionalListener;\n");
        source.append("import org.dempsay.utils.exceptional.api.ExceptionalResponse;\n\n");
        source.append("public final class ").append(builderName).append(" {\n");

        for (RecordComponentModel component : components) {
            source.append("  private ").append(component.typeName()).append(' ').append(component.name()).append(";\n\n");
        }

        source.append("  public ").append(builderName).append("() {}\n\n");
        source.append("  public ").append(builderName).append('(').append(recordSimpleName)
                .append(" source) {\n");
        source.append("    if (source != null) {\n");
        for (RecordComponentModel component : components) {
            source.append("      this.").append(component.name()).append(" = source.")
                    .append(component.name()).append("();\n");
        }
        source.append("    }\n");
        source.append("  }\n\n");

        for (RecordComponentModel component : components) {
            source.append("  public ").append(builderName).append(' ')
                    .append(component.name()).append('(').append(component.typeName())
                    .append(' ').append(component.name()).append(") {\n");
            source.append("    this.").append(component.name()).append(" = ")
                    .append(component.name()).append(";\n");
            source.append("    return this;\n");
            source.append("  }\n\n");
        }

        source.append("  public ExceptionalResponse<").append(recordSimpleName)
                .append("> build(ExceptionalListener onError) {\n");
        source.append("    var errors = new ArrayList<String>();\n\n");

        for (RecordComponentModel component : components) {
            appendValidation(source, component);
        }

        source.append("    if (!errors.isEmpty()) {\n");
        source.append("      onError.onError(new ValidationException(errors));\n");
        source.append("      return ExceptionalResponse.failure();\n");
        source.append("    }\n\n");
        source.append("    return ExceptionalResponse.success(new ")
                .append(recordSimpleName).append('(');
        for (int i = 0; i < components.size(); i++) {
            if (i > 0) {
                source.append(", ");
            }
            source.append(components.get(i).name());
        }
        source.append("));\n");
        source.append("  }\n");
        source.append("}\n");
        return source.toString();
    }

    private static void appendValidation(final StringBuilder source, final RecordComponentModel component) {
        final String field = component.name();
        if (!component.nullable()) {
            source.append("    if (").append(field).append(" == null) {\n");
            source.append("      errors.add(\"Field '").append(field).append("' must not be null\");\n");
            source.append("    }");
            if (component.isString() && hasStringConstraints(component)) {
                source.append(" else {\n");
                appendStringChecks(source, component, "      ");
                source.append("    }\n");
            } else {
                source.append("\n");
            }
        } else if (component.isString() && hasStringConstraints(component)) {
            source.append("    if (").append(field).append(" != null) {\n");
            appendStringChecks(source, component, "      ");
            source.append("    }\n");
        }
        source.append("\n");
    }

    private static boolean hasStringConstraints(final RecordComponentModel component) {
        return component.minLength() != null
                || component.maxLength() != null
                || component.hasRegex();
    }

    private static void appendStringChecks(
            final StringBuilder source,
            final RecordComponentModel component,
            final String indent) {
        final String field = component.name();
        if (component.minLength() != null && component.maxLength() != null) {
            source.append(indent).append("int ").append(field).append("Len = ").append(field).append(".length();\n");
            source.append(indent).append("if (").append(field).append("Len < ")
                    .append(component.minLength()).append(" || ").append(field)
                    .append("Len > ").append(component.maxLength()).append(") {\n");
            source.append(indent).append("  errors.add(\"Field '").append(field)
                    .append("' length must be between ").append(component.minLength())
                    .append(" and ").append(component.maxLength()).append(", got \" + ")
                    .append(field).append("Len);\n");
            source.append(indent).append("}\n");
        } else if (component.minLength() != null) {
            source.append(indent).append("int ").append(field).append("Len = ").append(field).append(".length();\n");
            source.append(indent).append("if (").append(field).append("Len < ")
                    .append(component.minLength()).append(") {\n");
            source.append(indent).append("  errors.add(\"Field '").append(field)
                    .append("' length must be at least ").append(component.minLength())
                    .append(", got \" + ").append(field).append("Len);\n");
            source.append(indent).append("}\n");
        } else if (component.maxLength() != null) {
            source.append(indent).append("int ").append(field).append("Len = ").append(field).append(".length();\n");
            source.append(indent).append("if (").append(field).append("Len > ")
                    .append(component.maxLength()).append(") {\n");
            source.append(indent).append("  errors.add(\"Field '").append(field)
                    .append("' length must be at most ").append(component.maxLength())
                    .append(", got \" + ").append(field).append("Len);\n");
            source.append(indent).append("}\n");
        }

        if (component.hasRegex()) {
            source.append(indent).append("if (!Pattern.matches(\"")
                    .append(escapeJava(component.regexPattern())).append("\", ")
                    .append(field).append(")) {\n");
            source.append(indent).append("  errors.add(\"Field '").append(field)
                    .append("' does not match pattern\");\n");
            source.append(indent).append("}\n");
        }
    }

    private static String escapeJava(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}