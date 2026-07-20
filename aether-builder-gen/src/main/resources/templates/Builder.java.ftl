package ${packageName};

import java.util.ArrayList;
import java.util.List;
<#if needsPattern>
import java.util.regex.Pattern;
</#if>

import org.dempsay.aether.api.builder.AetherBuilder;
import org.dempsay.aether.api.validation.ValidationException;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
<#list viewInterfaces as v>
<#if v.needsImport>
import ${v.qualifiedName};
</#if>
</#list>

/**
 * Generated builder for {@link ${recordName}}.
 */
public final class ${builderName} implements AetherBuilder<${recordName}><#if viewInterfaces?has_content>, <#list viewInterfaces as v>${v.simpleName}<#sep>, </#list></#if> {
<#list components as c>
<#if c.hasRegex()>
    private static final Pattern ${c.name?upper_case}_PATTERN = Pattern.compile("${c.regexPattern?js_string}");
</#if>
</#list>
<#list components as c>
    private ${c.typeName} ${c.name};

</#list>
    /**
     * Creates an empty builder.
     */
    public ${builderName}() { }

    /**
     * Creates a builder initialized from an existing record (or empty if null).
     *
     * @param source existing record, or {@code null}
     */
    public ${builderName}(final ${recordName} source) {
        if (source != null) {
<#list components as c>
            this.${c.name} = source.${c.name}();
</#list>
        }
    }

<#list components as c>
    /**
     * @return current {@code ${c.name}} value
     */
    public ${c.typeName} ${c.name}() {
        return ${c.name};
    }

    /**
     * @param ${c.name} value to set
     * @return this builder
     */
    public ${builderName} ${c.name}(final ${c.typeName} ${c.name}) {
        this.${c.name} = ${c.name};
        return this;
    }

</#list>
    private List<String> collectValidationErrors() {
        final List<String> errors = new ArrayList<>();

<#list components as c>
    <#if !c.nullable>
        if (${c.name} == null) {
            errors.add("Field '${c.name}' must not be null");
        }<#if c.string && c.hasStringConstraints()> else {
            <#assign indent = "            ">
            <#include "validation.java.ftl">
        }</#if>
    <#elseif c.string && c.hasStringConstraints()>
        if (${c.name} != null) {
            <#assign indent = "            ">
            <#include "validation.java.ftl">
        }
    </#if>

</#list>
        return errors;
    }

    private ExceptionalResponse<${recordName}> buildRecord(final ExceptionalListener onError) {
        final List<String> errors = collectValidationErrors();
        if (!errors.isEmpty()) {
            onError.accept(new ValidationException(errors));
            return ExceptionalResponse.failure();
        }

        return ExceptionalResponse.success(new ${recordName}(<#list components as c>${c.name}<#sep>, </#list>));
    }

    @Override
    public ExceptionalResponse<${recordName}> build(final ExceptionalListener onError) {
        return buildRecord(onError);
    }
}