package ${packageName};

<#if singleton>
import org.dempsay.aether.api.store.AetherSingletonStore;
<#else>
import org.dempsay.aether.api.store.AetherResourceStore;
</#if>

/**
 * Generated store port for {@link ${recordName}}.
 *
 * <p>Empty interface for OSGi SCR / type-based injection; extends the generic
 * Aether store contract.
 */
public interface ${storeName} extends <#if singleton>AetherSingletonStore<#else>AetherResourceStore</#if><${recordName}> {
}
