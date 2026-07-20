package org.dempsay.aether.store.fs;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Path helpers for the filesystem provider layout.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
final class FileSystemPaths {
    static final String SINGLETON_FILE = "_singleton.json";

    private FileSystemPaths() {
    }

    static Path typeDirectory(final Path root, final String resourceType) {
        return root.resolve(resourceType);
    }

    static Path documentPath(final Path typeDir, final String id) {
        return typeDir.resolve(id + ".json");
    }

    static Path singletonPath(final Path typeDir) {
        return typeDir.resolve(SINGLETON_FILE);
    }

    /**
     * Returns an error message if {@code id} is unsafe for a file name, else null.
     *
     * @param id candidate resource id
     * @return validation message or null if ok
     */
    static String invalidIdReason(final String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()
                || id.contains("/")
                || id.contains("\\")
                || id.contains("..")
                || id.startsWith(".")) {
            return "Invalid resource id for filesystem store: " + id;
        }
        return null;
    }
}
