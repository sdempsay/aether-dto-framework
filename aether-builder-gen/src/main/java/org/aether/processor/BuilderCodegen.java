package org.aether.processor;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

/**
 * Renders generated builder source from FreeMarker templates.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
final class BuilderCodegen {
    private static final Configuration CONFIGURATION = createConfiguration();

    private BuilderCodegen() {
    }

    /**
     * Renders a validated builder source file for the given record model.
     *
     * @param packageName target package for the generated builder
     * @param recordSimpleName simple name of the annotated record
     * @param components record component metadata
     * @param viewInterfaces interfaces the generated builder should implement
     * @return generated Java source
     * @throws IOException when the template cannot be read
     * @throws TemplateException when template evaluation fails
     */
    static String render(
            final String packageName,
            final String recordSimpleName,
            final List<RecordComponentModel> components,
            final List<InterfaceViewModel> viewInterfaces) throws IOException, TemplateException {
        final Template template = CONFIGURATION.getTemplate("Builder.ftl");
        final Map<String, Object> model = new HashMap<>();
        model.put("packageName", packageName);
        model.put("recordName", recordSimpleName);
        model.put("builderName", recordSimpleName + "Builder");
        model.put("components", components);
        model.put("viewInterfaces", viewInterfaces.stream().map(BuilderCodegen::toTemplateModel).toList());
        model.put("needsPattern", components.stream().anyMatch(RecordComponentModel::hasRegex));

        final StringWriter output = new StringWriter();
        template.process(model, output);
        return output.toString();
    }

    /**
     * Adapts a view model for FreeMarker property access.
     *
     * @param view the interface view metadata
     * @return a template-friendly property map
     */
    private static Map<String, Object> toTemplateModel(final InterfaceViewModel view) {
        final Map<String, Object> model = new HashMap<>();
        model.put("simpleName", view.simpleName());
        model.put("qualifiedName", view.qualifiedName());
        model.put("needsImport", view.needsImport());
        return model;
    }

    private static Configuration createConfiguration() {
        final Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setClassLoaderForTemplateLoading(BuilderCodegen.class.getClassLoader(), "templates");
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setFallbackOnNullLoopVariable(false);
        return configuration;
    }
}