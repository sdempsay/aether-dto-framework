/**
 * Server-side store provider generation surface for Aether.
 *
 * <p>Contains {@link org.dempsay.aether.store.gen.AetherStoreProviders} (T5b) and,
 * later, the JSR-269 processor that emits Fs/Memory {@code *Store} adapters (T5c).
 * Depend on this module from <strong>server/impl</strong> modules only — not from
 * pure DTO api bundles.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
package org.dempsay.aether.store.gen;
