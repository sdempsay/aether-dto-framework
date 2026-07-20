package org.dempsay.aether.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt-in marker that triggers generation of a validated builder for a flat record DTO.
 *
 * <p>{@link RetentionPolicy#CLASS} so multi-module processors (e.g. {@code aether-store-gen}
 * reading api-module class files) can still detect the marker. Source retention would
 * drop it from the compiled artifact.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AetherRecord {
}
