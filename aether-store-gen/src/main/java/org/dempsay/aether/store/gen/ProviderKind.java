package org.dempsay.aether.store.gen;

/**
 * Backend kind for a generated store adapter.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
enum ProviderKind {
    /** Multi-resource filesystem store. */
    FILESYSTEM(
            "filesystem",
            "Fs",
            "FileSystemAetherResourceStore",
            "org.dempsay.aether.store.fs.FileSystemAetherResourceStore",
            true),

    /** Singleton filesystem store. */
    SINGLETON_FILESYSTEM(
            "singleton filesystem",
            "Fs",
            "FileSystemAetherSingletonStore",
            "org.dempsay.aether.store.fs.FileSystemAetherSingletonStore",
            true),

    /** Multi-resource in-memory store. */
    MEMORY(
            "in-memory",
            "Memory",
            "InMemoryAetherResourceStore",
            "org.dempsay.aether.store.memory.InMemoryAetherResourceStore",
            false);

    private final String kindLabel;
    private final String namePrefix;
    private final String superClassSimpleName;
    private final String superClassQualifiedName;
    private final boolean needsPathConstructor;

    ProviderKind(
            final String kindLabel,
            final String namePrefix,
            final String superClassSimpleName,
            final String superClassQualifiedName,
            final boolean needsPathConstructor) {
        this.kindLabel = kindLabel;
        this.namePrefix = namePrefix;
        this.superClassSimpleName = superClassSimpleName;
        this.superClassQualifiedName = superClassQualifiedName;
        this.needsPathConstructor = needsPathConstructor;
    }

    String kindLabel() {
        return kindLabel;
    }

    String adapterSimpleName(final String recordSimpleName) {
        return namePrefix + recordSimpleName + "Store";
    }

    String superClassSimpleName() {
        return superClassSimpleName;
    }

    String superClassQualifiedName() {
        return superClassQualifiedName;
    }

    boolean needsPathConstructor() {
        return needsPathConstructor;
    }

    boolean requiresSingletonAnnotation() {
        return this == SINGLETON_FILESYSTEM;
    }

    boolean rejectsSingletonAnnotation() {
        return this == FILESYSTEM || this == MEMORY;
    }
}
