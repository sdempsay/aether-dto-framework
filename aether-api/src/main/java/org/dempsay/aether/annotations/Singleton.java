package org.dempsay.aether.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@link AetherRecord} type as a singleton resource (cardinality ≤ 1).
 *
 * <p>Use with {@link org.dempsay.aether.store.api.AetherSingletonStore}; there is no
 * caller-facing id on the public API. Runtime retention allows tooling and
 * providers to detect the marker without codegen.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Singleton {
}
