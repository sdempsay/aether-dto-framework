package org.dempsay.aether.store.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.dempsay.aether.access.AetherPrincipal;
import org.dempsay.aether.failure.AetherException;
import org.dempsay.aether.failure.AetherFailure;
import org.dempsay.aether.store.AetherPersisted;
import org.dempsay.aether.store.UpdateOptions;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestInMemoryAetherSingletonStore {
    private InMemoryAetherSingletonStore<String> store;
    private AetherPrincipal system;
    private AtomicReference<Exception> error;

    @BeforeEach
    void setUp() {
        store = new InMemoryAetherSingletonStore<>();
        system = AetherPrincipal.system();
        error = new AtomicReference<>();
    }

    @Test
    void createReadUpdateDelete() {
        final AetherPersisted<String> created =
                store.create(error::set, system, "config-v1").response();
        assertEquals(InMemoryAetherSingletonStore.SINGLETON_ID, created.metadata().id());
        assertEquals("config-v1", created.resource());

        assertEquals("config-v1", store.read(error::set, system).response().resource());

        final String version = created.metadata().version();
        final AetherPersisted<String> updated = store.update(
                error::set,
                system,
                "config-v2",
                version,
                UpdateOptions.defaults()).response();
        assertEquals("config-v2", updated.resource());

        assertTrue(store.delete(error::set, system).wasNoError());
        assertTrue(store.read(error::set, system).wasError());
        assertFailure(AetherFailure.NotFound);
    }

    @Test
    void secondCreateConflicts() {
        store.create(error::set, system, "once");
        error.set(null);
        final ExceptionalResponse<AetherPersisted<String>> response =
                store.create(error::set, system, "twice");
        assertTrue(response.wasError());
        assertFailure(AetherFailure.Conflict);
    }

    private void assertFailure(final AetherFailure expected) {
        assertInstanceOf(AetherException.class, error.get());
        assertEquals(expected, ((AetherException) error.get()).failure());
    }
}
