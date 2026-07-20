package org.dempsay.aether.store.gen;

/**
 * Template inputs for one generated provider adapter.
 *
 * @param packageName package of the generated adapter
 * @param recordSimpleName simple name of the DTO record
 * @param recordQualifiedName fully qualified record name
 * @param storeSimpleName simple name of the store interface
 * @param storeQualifiedName fully qualified store interface name
 * @param kind provider kind
 * @param scr whether to emit OSGi DS annotations
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
record ProviderAdapterModel(
        String packageName,
        String recordSimpleName,
        String recordQualifiedName,
        String storeSimpleName,
        String storeQualifiedName,
        ProviderKind kind,
        boolean scr) {
}
