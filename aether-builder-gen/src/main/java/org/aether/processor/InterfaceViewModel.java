package org.aether.processor;

/**
 * A record-implemented interface for which the generated builder exposes a typed build view.
 *
 * @since 0.1.0
 */
public final class InterfaceViewModel {
    private final String simpleName;
    private final String qualifiedName;
    private final boolean needsImport;

    InterfaceViewModel(
            final String simpleName,
            final String qualifiedName,
            final boolean needsImport) {
        this.simpleName = simpleName;
        this.qualifiedName = qualifiedName;
        this.needsImport = needsImport;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public boolean isNeedsImport() {
        return needsImport;
    }
}