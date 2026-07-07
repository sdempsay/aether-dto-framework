package org.aether.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.aether.annotations.AetherRecord;
import org.aether.annotations.MaxLength;
import org.aether.annotations.MinLength;
import org.aether.annotations.Nullable;
import org.aether.annotations.RegexMatch;
import org.dempsay.support.jsr269.annotation.Jsr269Processor;
import org.dempsay.utils.exceptional.api.ExceptionalAction;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Generates validated builders for {@link AetherRecord}-annotated flat record DTOs.
 *
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
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elements = processingEnv.getElementUtils();
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

            generateBuilder(record, components.get());
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

    private void generateBuilder(final TypeElement record, final List<RecordComponentModel> components) {
        final String packageName = elements.getPackageOf(record).getQualifiedName().toString();
        final String recordSimpleName = record.getSimpleName().toString();

        final ExceptionalResponse<String> rendered = ExceptionalSupplier
                .of(() -> BuilderCodegen.render(packageName, recordSimpleName, components))
                .with(e -> error(record, "Failed to generate builder: " + e.getMessage()))
                .execute();

        if (rendered.wasNoError()) {
            ExceptionalAction
                    .of(() -> writeBuilderSource(record, packageName, recordSimpleName, rendered.response()))
                    .with(e -> error(record, "Failed to write builder: " + e.getMessage()))
                    .execute();
        }
    }

    private void writeBuilderSource(
            final TypeElement record,
            final String packageName,
            final String recordSimpleName,
            final String source) throws IOException {
        final JavaFileObject sourceFile = filer.createSourceFile(packageName + "." + recordSimpleName + "Builder", record);
        try (var writer = sourceFile.openWriter()) {
            writer.write(source);
        }
    }

    private void error(final Element element, final String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}