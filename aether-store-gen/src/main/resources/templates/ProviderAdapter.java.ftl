package ${packageName};

<#if needsPath>
import java.nio.file.Path;
<#if scr>
import java.util.Map;
import java.util.Objects;

</#if>
</#if>
<#if scr>
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

</#if>
import ${superClassQualified};
import ${recordQualifiedName};
import ${storeQualifiedName};

/**
 * Generated ${kindLabel} provider for {@link ${recordSimpleName}}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
<#if scr>
@Component(service = ${storeSimpleName}.class)
</#if>
public class ${adapterSimpleName} extends ${superClassSimpleName}<${recordSimpleName}>
        implements ${storeSimpleName} {
<#if needsPath>
<#if scr>
    /**
     * SCR activation: component property {@code root} (string) is the storage root.
     *
     * @param properties DS component properties
     */
    @Activate
    public ${adapterSimpleName}(final Map<String, ?> properties) {
        this(Path.of(Objects.requireNonNull(properties.get("root"), "root").toString()));
    }

</#if>
    /**
     * Opens this store under {@code root}.
     *
     * @param root storage root directory
     */
    public ${adapterSimpleName}(final Path root) {
        super(root, ${recordSimpleName}.class);
    }
<#else>
    /**
     * Creates an in-memory store for {@link ${recordSimpleName}}.
     */
<#if scr>
    @Activate
</#if>
    public ${adapterSimpleName}() {
        super(${recordSimpleName}.class);
    }
</#if>
}
