package org.dempsay.aether.store.fs;

import org.dempsay.aether.api.store.config.FileStoreConfig;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi Config Admin–backed {@link FileStoreConfig} for filesystem stores.
 *
 * <p>Generated {@code Fs*Store} adapters reference this (or any other
 * {@link FileStoreConfig} service) via DS {@code @Reference}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
@Component(
        service = FileStoreConfig.class,
        configurationPid = "org.dempsay.aether.store.fs",
        configurationPolicy = ConfigurationPolicy.OPTIONAL)
@Designate(ocd = FileStoreConfigService.Config.class)
public class FileStoreConfigService implements FileStoreConfig {
    private volatile String location = DEFAULT_LOCATION;

    /**
     * Applies Config Admin / metatype configuration.
     *
     * @param config typed configuration
     */
    @Activate
    public void activate(final Config config) {
        this.location = config.location();
    }

    @Override
    public String location() {
        return location;
    }

    /**
     * Metatype for the filesystem store root.
     */
    @ObjectClassDefinition(
            name = "Aether File Store Config",
            description = "Configuration for Aether filesystem persistence")
    public @interface Config {
        /**
         * @return location of the file persistence root
         */
        @AttributeDefinition(
                description = "Location of the file persistence root",
                required = false,
                defaultValue = FileStoreConfig.DEFAULT_LOCATION)
        String location() default FileStoreConfig.DEFAULT_LOCATION;
    }
}
