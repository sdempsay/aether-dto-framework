package org.aether.processor;

/**
 * A record-implemented interface that the generated builder also implements.
 *
 * @param simpleName the interface simple name used in generated method names
 * @param qualifiedName the fully qualified interface name for imports
 * @param needsImport whether the generated builder requires an explicit import
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
record InterfaceViewModel(String simpleName, String qualifiedName, boolean needsImport) {
}