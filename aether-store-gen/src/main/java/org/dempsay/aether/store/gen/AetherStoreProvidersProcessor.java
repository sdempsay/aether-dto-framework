package org.dempsay.aether.store.gen;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.dempsay.aether.annotations.AetherRecord;
import org.dempsay.aether.annotations.Singleton;
import org.dempsay.support.jsr269.annotation.Jsr269Processor;
import org.dempsay.utils.exceptional.api.ExceptionalListener;
import org.dempsay.utils.exceptional.api.ExceptionalResourceAction;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Generates Fs/Memory provider adapter classes for types listed on
 * {@link AetherStoreProviders}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
@Jsr269Processor
@SupportedAnnotationTypes("org.dempsay.aether.store.gen.AetherStoreProviders")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class AetherStoreProvidersProcessor extends AbstractProcessor {
    private static final String ANNOTATION_NAME = AetherStoreProviders.class.getCanonicalName();

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
        for (final Element element : roundEnv.getElementsAnnotatedWith(AetherStoreProviders.class)) {
            processProviders(element);
        }
        return false;
    }

    private void processProviders(final Element annotated) {
        final String packageName = resolvePackageName(annotated);
        if (packageName == null || packageName.isEmpty()) {
            error(annotated, "@AetherStoreProviders must be on a named package or a type in a package");
            return;
        }

        final List<TypeElement> filesystem = typesFromMember(annotated, "filesystem");
        final List<TypeElement> singletonFilesystem = typesFromMember(annotated, "singletonFilesystem");
        final List<TypeElement> memory = typesFromMember(annotated, "memory");

        if (filesystem.isEmpty() && singletonFilesystem.isEmpty() && memory.isEmpty()) {
            error(annotated, "@AetherStoreProviders requires at least one non-empty list "
                    + "(filesystem, singletonFilesystem, or memory)");
            return;
        }

        for (final TypeElement type : filesystem) {
            generateIfValid(annotated, packageName, type, ProviderKind.FILESYSTEM);
        }
        for (final TypeElement type : singletonFilesystem) {
            generateIfValid(annotated, packageName, type, ProviderKind.SINGLETON_FILESYSTEM);
        }
        for (final TypeElement type : memory) {
            generateIfValid(annotated, packageName, type, ProviderKind.MEMORY);
        }
    }

    private void generateIfValid(
            final Element annotated,
            final String packageName,
            final TypeElement recordType,
            final ProviderKind kind) {
        if (!validateRecord(annotated, recordType, kind)) {
            return;
        }

        final String recordSimpleName = recordType.getSimpleName().toString();
        final String recordQualifiedName = recordType.getQualifiedName().toString();
        final String recordPackage = elements.getPackageOf(recordType).getQualifiedName().toString();
        final String storeSimpleName = recordSimpleName + "Store";
        final String storeQualifiedName = recordPackage.isEmpty()
                ? storeSimpleName
                : recordPackage + "." + storeSimpleName;
        final String adapterSimpleName = kind.adapterSimpleName(recordSimpleName);

        final ExceptionalResponse<String> rendered = ProviderCodegen.render(
                packageName,
                recordSimpleName,
                recordQualifiedName,
                storeSimpleName,
                storeQualifiedName,
                kind,
                e -> error(annotated, "Failed to generate " + adapterSimpleName + ": " + e.getMessage()));

        if (rendered.wasError()) {
            return;
        }

        final ExceptionalResponse<Writer> writerResponse = openWriter(
                annotated,
                packageName,
                adapterSimpleName,
                e -> error(annotated, "Failed to open writer for " + adapterSimpleName + ": " + e.getMessage()));

        if (writerResponse.wasNoError()) {
            ExceptionalResourceAction
                    .of(() -> writerResponse.response(), writer -> writer.write(rendered.response()))
                    .with(e -> error(annotated, "Failed to write " + adapterSimpleName + ": " + e.getMessage()))
                    .execute();
        }
    }

    private boolean validateRecord(
            final Element annotated,
            final TypeElement type,
            final ProviderKind kind) {
        if (type.getKind() != ElementKind.RECORD) {
            error(annotated, kind.kindLabel() + " entry '" + type.getQualifiedName()
                    + "' must be a record");
            return false;
        }
        if (type.getAnnotation(AetherRecord.class) == null) {
            error(annotated, kind.kindLabel() + " entry '" + type.getQualifiedName()
                    + "' must be annotated with @AetherRecord");
            return false;
        }
        final boolean singleton = type.getAnnotation(Singleton.class) != null;
        if (kind.requiresSingletonAnnotation() && !singleton) {
            error(annotated, "singletonFilesystem entry '" + type.getQualifiedName()
                    + "' must be annotated with @Singleton");
            return false;
        }
        if (kind.rejectsSingletonAnnotation() && singleton) {
            error(annotated, kind.kindLabel() + " entry '" + type.getQualifiedName()
                    + "' is @Singleton; list it under singletonFilesystem instead");
            return false;
        }
        return true;
    }

    private String resolvePackageName(final Element annotated) {
        if (annotated.getKind() == ElementKind.PACKAGE) {
            return ((PackageElement) annotated).getQualifiedName().toString();
        }
        return elements.getPackageOf(annotated).getQualifiedName().toString();
    }

    private List<TypeElement> typesFromMember(final Element annotated, final String memberName) {
        final List<TypeElement> result = new ArrayList<>();
        final AnnotationMirror mirror = findProvidersMirror(annotated);
        if (mirror == null) {
            return result;
        }

        final Map<? extends ExecutableElement, ? extends AnnotationValue> values =
                elements.getElementValuesWithDefaults(mirror);
        for (final Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : values.entrySet()) {
            if (!entry.getKey().getSimpleName().contentEquals(memberName)) {
                continue;
            }
            final Object raw = entry.getValue().getValue();
            if (!(raw instanceof List<?> list)) {
                continue;
            }
            for (final Object item : list) {
                if (!(item instanceof AnnotationValue av)) {
                    continue;
                }
                final Object value = av.getValue();
                if (value instanceof TypeMirror typeMirror) {
                    final Element typeElement = types.asElement(typeMirror);
                    if (typeElement instanceof TypeElement te) {
                        result.add(te);
                    }
                }
            }
        }
        return result;
    }

    private AnnotationMirror findProvidersMirror(final Element annotated) {
        for (final AnnotationMirror mirror : annotated.getAnnotationMirrors()) {
            final Element annType = types.asElement(mirror.getAnnotationType());
            if (annType instanceof TypeElement te
                    && ANNOTATION_NAME.equals(te.getQualifiedName().toString())) {
                return mirror;
            }
        }
        return null;
    }

    private ExceptionalResponse<Writer> openWriter(
            final Element originating,
            final String packageName,
            final String simpleName,
            final ExceptionalListener onError) {
        return ExceptionalSupplier.of(() -> {
            final JavaFileObject sourceFile = filer.createSourceFile(
                    packageName + "." + simpleName, originating);
            return sourceFile.openWriter();
        }).with(onError).execute();
    }

    private void error(final Element element, final String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
