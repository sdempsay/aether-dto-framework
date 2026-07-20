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
     * @param model adapter template inputs
     * @param onError failure listener
     * @return generated Java source
     */
    static ExceptionalResponse<String> render(
            final ProviderAdapterModel model,
            final ExceptionalListener onError) {
        return ExceptionalSupplier.of(() -> {
            final Template template = CONFIGURATION.getTemplate("ProviderAdapter.java.ftl");
            final ProviderKind kind = model.kind();
            final Map<String, Object> freemarkerModel = new HashMap<>();
            freemarkerModel.put("packageName", model.packageName());
            freemarkerModel.put("recordSimpleName", model.recordSimpleName());
            freemarkerModel.put("recordQualifiedName", model.recordQualifiedName());
            freemarkerModel.put("storeSimpleName", model.storeSimpleName());
            freemarkerModel.put("storeQualifiedName", model.storeQualifiedName());
            freemarkerModel.put("adapterSimpleName", kind.adapterSimpleName(model.recordSimpleName()));
            freemarkerModel.put("superClassSimpleName", kind.superClassSimpleName());
            freemarkerModel.put("superClassQualified", kind.superClassQualifiedName());
            freemarkerModel.put("needsPath", kind.needsPathConstructor());
            freemarkerModel.put("kindLabel", kind.kindLabel());
            freemarkerModel.put("scr", model.scr());

            final StringWriter output = new StringWriter();
            template.process(freemarkerModel, output);
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
