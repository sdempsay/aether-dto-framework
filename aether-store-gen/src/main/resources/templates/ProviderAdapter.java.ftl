package ${packageName};

<#if needsPath>
import java.nio.file.Path;
<#if scr>

import org.dempsay.aether.store.api.config.FileStoreConfig;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
</#if>
<#elseif scr>
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
     * SCR activation: injects {@link FileStoreConfig} for the storage root.
     *
     * @param config filesystem store configuration service
     */
    @Activate
    public ${adapterSimpleName}(@Reference final FileStoreConfig config) {
        this(Path.of(config.location()));
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
