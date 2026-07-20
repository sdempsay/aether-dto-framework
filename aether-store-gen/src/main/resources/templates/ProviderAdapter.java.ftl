package ${packageName};

<#if needsPath>
import java.nio.file.Path;

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
public class ${adapterSimpleName} extends ${superClassSimpleName}<${recordSimpleName}>
        implements ${storeSimpleName} {
<#if needsPath>
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
    public ${adapterSimpleName}() {
        super(${recordSimpleName}.class);
    }
</#if>
}
