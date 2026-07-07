package ${packageName};

import java.util.ArrayList;
import java.util.List;
<#if needsPattern>
import java.util.regex.Pattern;
</#if>
import org.aether.builder.AetherBuilder;
import org.aether.validation.ValidationException;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
<#list viewInterfaces as v>
<#if v.needsImport>
import ${v.qualifiedName};
</#if>
</#list>

public final class ${builderName} implements AetherBuilder<${recordName}> {
<#list components as c>
    private ${c.typeName} ${c.name};

</#list>
    public ${builderName}() {}

    public ${builderName}(${recordName} source) {
        if (source != null) {
<#list components as c>
            this.${c.name} = source.${c.name}();
</#list>
        }
    }

<#list components as c>
    public ${builderName} ${c.name}(${c.typeName} ${c.name}) {
        this.${c.name} = ${c.name};
        return this;
    }

</#list>
    private List<String> collectValidationErrors() {
        var errors = new ArrayList<String>();

<#list components as c>
    <#if !c.nullable>
        if (${c.name} == null) {
            errors.add("Field '${c.name}' must not be null");
        }<#if c.string && c.hasStringConstraints()> else {
            <#assign indent = "            ">
            <#include "validation.ftl">
        }</#if>
    <#elseif c.string && c.hasStringConstraints()>
        if (${c.name} != null) {
            <#assign indent = "            ">
            <#include "validation.ftl">
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
<#list viewInterfaces as v>

    /**
     * Builds a validated instance and returns it as {@link ${v.qualifiedName}}.
     *
     * @param onError invoked when validation fails
     * @return success with the interface view, or failure after {@code onError} is invoked
     */
    public ExceptionalResponse<${v.simpleName}> buildAs${v.simpleName}(final ExceptionalListener onError) {
        final ExceptionalResponse<${recordName}> built = buildRecord(onError);
        if (built.wasError()) {
            return ExceptionalResponse.failure();
        }

        return ExceptionalResponse.success((${v.simpleName}) built.response());
    }
</#list>
}