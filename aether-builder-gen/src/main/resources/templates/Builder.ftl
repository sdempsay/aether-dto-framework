package ${packageName};

import java.util.ArrayList;
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
  @Override
  public ExceptionalResponse<${recordName}> build(ExceptionalListener onError) {
    var errors = new ArrayList<String>();

<#list components as c>
  <#if !c.nullable>
    if (${c.name} == null) {
      errors.add("Field '${c.name}' must not be null");
    }<#if c.string && c.hasStringConstraints()> else {
      <#include "validation.ftl">
    }</#if>
  <#elseif c.string && c.hasStringConstraints()>
    if (${c.name} != null) {
      <#include "validation.ftl">
    }
  </#if>

</#list>
    if (!errors.isEmpty()) {
      onError.accept(new ValidationException(errors));
      return ExceptionalResponse.failure();
    }

    return ExceptionalResponse.success(new ${recordName}(<#list components as c>${c.name}<#sep>, </#list>));
  }
<#list viewInterfaces as v>

  public ExceptionalResponse<${v.simpleName}> buildAs${v.simpleName}(ExceptionalListener onError) {
    ExceptionalResponse<${recordName}> built = build(onError);
    if (built.wasError()) {
      return ExceptionalResponse.failure();
    }
    return ExceptionalResponse.success(built.response());
  }
</#list>
}