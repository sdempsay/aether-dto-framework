package ${packageName};

<#if singleton>
import org.aether.store.AetherSingletonStore;
<#else>
import org.aether.store.AetherResourceStore;
</#if>

/**
 * Generated store port for {@link ${recordName}}.
 *
 * <p>Empty interface for OSGi SCR / type-based injection; extends the generic
 * Aether store contract.
 */
public interface ${storeName} extends <#if singleton>AetherSingletonStore<#else>AetherResourceStore</#if><${recordName}> {
}
