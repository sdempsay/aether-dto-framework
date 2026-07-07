<#-- expects `c` (RecordComponentModel) and optional indent (default 12 spaces) -->
<#assign pad = indent! "            ">
<#if c.minLength?? && c.maxLength??>
${pad}int ${c.name}Len = ${c.name}.length();
${pad}if (${c.name}Len < ${c.minLength?c} || ${c.name}Len > ${c.maxLength?c}) {
${pad}    errors.add("Field '${c.name}' length must be between ${c.minLength?c} and ${c.maxLength?c}, got " + ${c.name}Len);
${pad}}
<#elseif c.minLength??>
${pad}int ${c.name}Len = ${c.name}.length();
${pad}if (${c.name}Len < ${c.minLength?c}) {
${pad}    errors.add("Field '${c.name}' length must be at least ${c.minLength?c}, got " + ${c.name}Len);
${pad}}
<#elseif c.maxLength??>
${pad}int ${c.name}Len = ${c.name}.length();
${pad}if (${c.name}Len > ${c.maxLength?c}) {
${pad}    errors.add("Field '${c.name}' length must be at most ${c.maxLength?c}, got " + ${c.name}Len);
${pad}}
</#if>
<#if c.hasRegex()>
${pad}if (!Pattern.matches("${c.regexPattern?js_string}", ${c.name})) {
${pad}    errors.add("Field '${c.name}' does not match pattern");
${pad}}
</#if>