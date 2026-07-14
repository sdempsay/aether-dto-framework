package org.aether.processor;

import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.aether.annotations.AetherRecord;
import org.aether.annotations.MaxLength;
import org.aether.annotations.MinLength;
import org.aether.annotations.Nullable;
import org.aether.annotations.RegexMatch;
import org.aether.annotations.Singleton;
import org.dempsay.support.jsr269.annotation.Jsr269Processor;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResourceAction;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Generates validated builders and store interfaces for {@link AetherRecord}
 * flat record DTOs.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
@Jsr269Processor
@SupportedAnnotationTypes("org.aether.annotations.AetherRecord")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class AetherBuilderProcessor extends AbstractProcessor {
    private static final Set<String> BOXED_PRIMITIVES = Set.of(
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Character",
            "java.lang.Float",
            "java.lang.Double");

    private Elements elements;
    private Types types;
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(AetherRecord.class)) {
            if (element.getKind() != ElementKind.RECORD) {
                error(element, "@AetherRecord can only be applied to records");
                continue;
            }

            final TypeElement record = (TypeElement) element;
            final Optional<List<RecordComponentModel>> components = collectComponents(record);
            if (components.isEmpty()) {
                continue;
            }

            final List<InterfaceViewModel> viewInterfaces = collectViewInterfaces(record, components.get());
            generateBuilder(record, components.get(), viewInterfaces);
            generateStore(record);
        }
        return false;
    }

    private Optional<List<RecordComponentModel>> collectComponents(final TypeElement record) {
        final List<RecordComponentModel> components = new ArrayList<>();
        boolean valid = true;

        for (Element enclosed : record.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.RECORD_COMPONENT) {
                continue;
            }

            final VariableElement component = (VariableElement) enclosed;
            final TypeMirror typeMirror = component.asType();
            if (!isSupportedType(typeMirror)) {
                error(component, "Unsupported field type '" + typeMirror
                        + "'. MVP supports String, primitives, and wrappers only.");
                valid = false;
                continue;
            }

            final boolean nullable = component.getAnnotation(Nullable.class) != null;
            final MinLength minLength = component.getAnnotation(MinLength.class);
            final MaxLength maxLength = component.getAnnotation(MaxLength.class);
            final RegexMatch regexMatch = component.getAnnotation(RegexMatch.class);
            final boolean isString = isStringType(typeMirror);

            if (!isString && hasStringOnlyAnnotation(minLength, maxLength, regexMatch)) {
                error(component, "Length and regex annotations apply to String fields only");
                valid = false;
                continue;
            }

            components.add(new RecordComponentModel(
                    component.getSimpleName().toString(),
                    typeName(typeMirror),
                    nullable,
                    minLength == null ? null : minLength.value(),
                    maxLength == null ? null : maxLength.value(),
                    regexMatch == null ? null : regexMatch.pattern()));
        }

        return valid ? Optional.of(components) : Optional.empty();
    }

    /**
     * Collects implemented interfaces whose accessors map to record components.
     *
     * @param record the annotated record element
     * @param components collected record component metadata
     * @return metadata for interfaces the generated builder should implement
     */
    private List<InterfaceViewModel> collectViewInterfaces(
            final TypeElement record,
            final List<RecordComponentModel> components) {
        final Map<String, TypeMirror> componentTypes = new HashMap<>();
        for (Element enclosed : record.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.RECORD_COMPONENT) {
                continue;
            }
            final VariableElement component = (VariableElement) enclosed;
            componentTypes.put(component.getSimpleName().toString(), component.asType());
        }

        final String packageName = elements.getPackageOf(record).getQualifiedName().toString();
        final Set<String> seenQualifiedNames = new LinkedHashSet<>();
        final List<InterfaceViewModel> views = new ArrayList<>();

        for (TypeMirror interfaceMirror : record.getInterfaces()) {
            if (interfaceMirror.getKind() != TypeKind.DECLARED) {
                continue;
            }

            final TypeElement interfaceElement = (TypeElement) types.asElement(interfaceMirror);
            if (!isViewCompatible(interfaceElement, componentTypes)) {
                continue;
            }

            final String qualifiedName = elements.getBinaryName(interfaceElement).toString();
            if (!seenQualifiedNames.add(qualifiedName)) {
                continue;
            }

            final String simpleName = interfaceElement.getSimpleName().toString();
            final boolean needsImport = !qualifiedName.startsWith("java.lang.")
                    && !elements.getPackageOf(interfaceElement).getQualifiedName().contentEquals(packageName);
            views.add(new InterfaceViewModel(simpleName, qualifiedName, needsImport));
        }

        return views;
    }

    /**
     * Returns whether every abstract interface accessor maps to a compatible record component.
     *
     * @param interfaceElement the candidate view interface
     * @param componentTypes record component names to type mirrors
     * @return true when the generated builder can implement the interface
     */
    private boolean isViewCompatible(
            final TypeElement interfaceElement,
            final Map<String, TypeMirror> componentTypes) {
        final List<ExecutableElement> accessorMethods = new ArrayList<>();
        collectAbstractInterfaceMethods(interfaceElement, accessorMethods, new LinkedHashSet<>());

        for (ExecutableElement method : accessorMethods) {
            final String methodName = method.getSimpleName().toString();
            final TypeMirror componentType = componentTypes.get(methodName);
            if (componentType == null) {
                return false;
            }
            if (!method.getParameters().isEmpty()) {
                return false;
            }
            if (!returnTypeMatchesComponent(method.getReturnType(), componentType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Recursively collects abstract, non-private instance methods declared on an interface tree.
     *
     * @param interfaceElement the interface to inspect
     * @param methods destination list for collected accessor methods
     * @param visited qualified names already visited to avoid cycles
     */
    private void collectAbstractInterfaceMethods(
            final TypeElement interfaceElement,
            final List<ExecutableElement> methods,
            final Set<String> visited) {
        final String qualifiedName = elements.getBinaryName(interfaceElement).toString();
        if (!visited.add(qualifiedName)) {
            return;
        }

        for (TypeMirror superInterface : interfaceElement.getInterfaces()) {
            if (superInterface.getKind() == TypeKind.DECLARED) {
                collectAbstractInterfaceMethods(
                        (TypeElement) types.asElement(superInterface),
                        methods,
                        visited);
            }
        }

        for (Element enclosed : interfaceElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }

            final ExecutableElement method = (ExecutableElement) enclosed;
            if (method.getModifiers().contains(Modifier.STATIC)
                    || method.getModifiers().contains(Modifier.PRIVATE)
                    || method.isDefault()) {
                continue;
            }
            methods.add(method);
        }
    }

    /**
     * Returns whether a record component type can satisfy an interface accessor return type.
     *
     * @param methodReturn the interface accessor return type
     * @param componentType the record component type
     * @return true when the component is assignable to the accessor return type
     */
    private boolean returnTypeMatchesComponent(
            final TypeMirror methodReturn,
            final TypeMirror componentType) {
        return types.isSameType(methodReturn, componentType)
                || types.isSubtype(componentType, methodReturn);
    }

    private boolean isSupportedType(final TypeMirror typeMirror) {
        final TypeKind kind = typeMirror.getKind();
        if (kind.isPrimitive()) {
            return true;
        }
        if (kind == TypeKind.ARRAY) {
            return false;
        }
        if (kind != TypeKind.DECLARED) {
            return false;
        }

        final DeclaredType declaredType = (DeclaredType) typeMirror;
        final Element typeElement = declaredType.asElement();
        if (typeElement.getKind() == ElementKind.RECORD) {
            return false;
        }
        if (!declaredType.getTypeArguments().isEmpty()) {
            return false;
        }

        final String qualifiedName = elements.getBinaryName((TypeElement) typeElement).toString();
        return "java.lang.String".equals(qualifiedName) || BOXED_PRIMITIVES.contains(qualifiedName);
    }

    private boolean isStringType(final TypeMirror typeMirror) {
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return false;
        }
        final Element typeElement = ((DeclaredType) typeMirror).asElement();
        return "java.lang.String".equals(elements.getBinaryName((TypeElement) typeElement).toString());
    }

    private boolean hasStringOnlyAnnotation(
            final MinLength minLength,
            final MaxLength maxLength,
            final RegexMatch regexMatch) {
        return minLength != null || maxLength != null || regexMatch != null;
    }

    private String typeName(final TypeMirror typeMirror) {
        if (typeMirror.getKind().isPrimitive()) {
            return typeMirror.toString();
        }
        if (typeMirror.getKind() == TypeKind.DECLARED) {
            final TypeElement typeElement = (TypeElement) ((DeclaredType) typeMirror).asElement();
            if ("java.lang.String".equals(elements.getBinaryName(typeElement).toString())) {
                return "String";
            }
            return typeElement.getSimpleName().toString();
        }
        return typeMirror.toString();
    }

    private void generateBuilder(
            final TypeElement record,
            final List<RecordComponentModel> components,
            final List<InterfaceViewModel> viewInterfaces) {
        final String packageName = elements.getPackageOf(record).getQualifiedName().toString();
        final String recordSimpleName = record.getSimpleName().toString();

        final ExceptionalResponse<String> rendered = BuilderCodegen.render(
                packageName,
                recordSimpleName,
                components,
                viewInterfaces,
                e -> error(record, "Failed to generate builder: " + e.getMessage()));

        if (rendered.wasNoError()) {
            final ExceptionalResponse<Writer> writerResponse = openWriter(
                    record,
                    packageName,
                    recordSimpleName + "Builder",
                    e -> error(record, "Failed to open builder writer: " + e.getMessage()));

            if (writerResponse.wasNoError()) {
                ExceptionalResourceAction
                        .of(() -> writerResponse.response(), writer -> writer.write(rendered.response()))
                        .with(e -> error(record, "Failed to write builder: " + e.getMessage()))
                        .execute();
            }
        }
    }

    /**
     * Generates {@code {Record}Store} extending the appropriate Aether store port
     * for OSGi SCR / type-based injection.
     *
     * @param record the annotated record element
     */
    private void generateStore(final TypeElement record) {
        final String packageName = elements.getPackageOf(record).getQualifiedName().toString();
        final String recordSimpleName = record.getSimpleName().toString();
        final boolean singleton = record.getAnnotation(Singleton.class) != null;

        final ExceptionalResponse<String> rendered = BuilderCodegen.renderStore(
                packageName,
                recordSimpleName,
                singleton,
                e -> error(record, "Failed to generate store: " + e.getMessage()));

        if (rendered.wasNoError()) {
            final ExceptionalResponse<Writer> writerResponse = openWriter(
                    record,
                    packageName,
                    recordSimpleName + "Store",
                    e -> error(record, "Failed to open store writer: " + e.getMessage()));

            if (writerResponse.wasNoError()) {
                ExceptionalResourceAction
                        .of(() -> writerResponse.response(), writer -> writer.write(rendered.response()))
                        .with(e -> error(record, "Failed to write store: " + e.getMessage()))
                        .execute();
            }
        }
    }

    /**
     * Opens a source file writer for a generated type.
     *
     * @param record the annotated record element
     * @param packageName target package
     * @param simpleName simple name of the generated type (e.g. {@code MyDtoBuilder})
     * @param onError invoked when the source file cannot be created
     * @return the filer writer, or failure when the source file cannot be created
     */
    private ExceptionalResponse<Writer> openWriter(
            final TypeElement record,
            final String packageName,
            final String simpleName,
            final ExceptionalListener onError) {
        return ExceptionalSupplier.of(() -> {
            final JavaFileObject sourceFile = filer.createSourceFile(
                    packageName + "." + simpleName, record);
            return sourceFile.openWriter();
        }).with(onError).execute();
    }

    private void error(final Element element, final String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
