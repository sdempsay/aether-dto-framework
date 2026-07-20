package org.dempsay.aether.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a uniqueness constraint on a record component for the persistence store.
 *
 * <p>If {@link #group()} is omitted or blank, the group name defaults to the component
 * name (single-field uniqueness). Components that share the same non-blank
 * {@code group} form a composite unique key.
 *
 * <p>Enforced by the store (cross-document), not fully by the builder. Retention is
 * {@link RetentionPolicy#RUNTIME} so providers can discover constraints without
 * codegen.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Unique {
    /**
     * Unique constraint group name. Blank means the field name alone.
     *
     * @return group id shared by composite key members
     */
    String group() default "";
}
