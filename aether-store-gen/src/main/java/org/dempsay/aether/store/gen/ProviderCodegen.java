package org.dempsay.aether.store.gen;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

/**
 * Renders generated provider adapter sources from FreeMarker templates.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
final class ProviderCodegen {
    private static final Configuration CONFIGURATION = createConfiguration();

    private ProviderCodegen() {
    }

    /**
     * Renders one provider adapter class.
     *
     * @param packageName package of the generated adapter (server package)
     * @param recordSimpleName simple name of the DTO record
     * @param recordQualifiedName fully qualified record name
     * @param storeSimpleName simple name of the generated store interface
     * @param storeQualifiedName fully qualified store interface name
     * @param kind provider kind
     * @param onError failure listener
     * @return generated Java source
     */
    static ExceptionalResponse<String> render(
            final String packageName,
            final String recordSimpleName,
            final String recordQualifiedName,
            final String storeSimpleName,
            final String storeQualifiedName,
            final ProviderKind kind,
            final ExceptionalListener onError) {
        return ExceptionalSupplier.of(() -> {
            final Template template = CONFIGURATION.getTemplate("ProviderAdapter.java.ftl");
            final Map<String, Object> model = new HashMap<>();
            model.put("packageName", packageName);
            model.put("recordSimpleName", recordSimpleName);
            model.put("recordQualifiedName", recordQualifiedName);
            model.put("storeSimpleName", storeSimpleName);
            model.put("storeQualifiedName", storeQualifiedName);
            model.put("adapterSimpleName", kind.adapterSimpleName(recordSimpleName));
            model.put("superClassSimpleName", kind.superClassSimpleName());
            model.put("superClassQualified", kind.superClassQualifiedName());
            model.put("needsPath", kind.needsPathConstructor());
            model.put("kindLabel", kind.kindLabel());

            final StringWriter output = new StringWriter();
            template.process(model, output);
            return output.toString();
        }).with(onError).execute();
    }

    private static Configuration createConfiguration() {
        final Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setClassLoaderForTemplateLoading(ProviderCodegen.class.getClassLoader(), "templates");
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setFallbackOnNullLoopVariable(false);
        return configuration;
    }
}
