package ${packageName};

import java.util.ArrayList;
<#if needsPattern>
import java.util.regex.Pattern;
</#if>
import org.aether.validation.ValidationException;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

public final class ${builderName} {
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
}