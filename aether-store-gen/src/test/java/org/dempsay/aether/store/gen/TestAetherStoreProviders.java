package org.dempsay.aether.store.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Smoke tests for the {@link AetherStoreProviders} annotation API (T5b).
 */
class TestAetherStoreProviders {
    @Test
    void metaRetentionIsSourceAndTargetsPackageOrType() {
        final Retention retention = AetherStoreProviders.class.getAnnotation(Retention.class);
        assertNotNull(retention);
        // SOURCE: compile/processor only — no OSGi Import-Package for the annotation type
        assertEquals(RetentionPolicy.SOURCE, retention.value());

        final Target target = AetherStoreProviders.class.getAnnotation(Target.class);
        assertNotNull(target);
        final Set<ElementType> types = Arrays.stream(target.value()).collect(Collectors.toSet());
        assertTrue(types.contains(ElementType.PACKAGE));
        assertTrue(types.contains(ElementType.TYPE));
        assertEquals(2, types.size());
    }

    @Test
    void annotationTypeDeclaresExpectedMembers() throws Exception {
        assertNotNull(AetherStoreProviders.class.getMethod("filesystem"));
        assertNotNull(AetherStoreProviders.class.getMethod("singletonFilesystem"));
        assertNotNull(AetherStoreProviders.class.getMethod("memory"));
        assertNotNull(AetherStoreProviders.class.getMethod("scr"));
        assertEquals(Class[].class, AetherStoreProviders.class.getMethod("filesystem").getReturnType());
        assertEquals(Class[].class, AetherStoreProviders.class.getMethod("singletonFilesystem").getReturnType());
        assertEquals(Class[].class, AetherStoreProviders.class.getMethod("memory").getReturnType());
        assertEquals(boolean.class, AetherStoreProviders.class.getMethod("scr").getReturnType());
    }
}
