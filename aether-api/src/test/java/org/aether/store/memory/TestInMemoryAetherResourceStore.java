package org.aether.store.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.aether.access.AetherPrincipal;
import org.aether.failure.AetherException;
import org.aether.failure.AetherFailure;
import org.aether.store.AetherPersisted;
import org.aether.store.UpdateOptions;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestInMemoryAetherResourceStore {
    private InMemoryAetherResourceStore<String> store;
    private AetherPrincipal alice;
    private AtomicReference<Exception> error;

    @BeforeEach
    void setUp() {
        store = new InMemoryAetherResourceStore<>();
        alice = AetherPrincipal.user("alice");
        error = new AtomicReference<>();
    }

    @Test
    void createAssignsIdAndMetadata() {
        final ExceptionalResponse<AetherPersisted<String>> response =
                store.create(error::set, alice, "hello");

        assertTrue(response.wasNoError());
        final AetherPersisted<String> persisted = response.response();
        assertEquals("hello", persisted.resource());
        assertEquals("alice", persisted.metadata().createdBy());
        assertEquals("alice", persisted.metadata().updatedBy());
        assertTrue(persisted.metadata().id().length() > 0);
        assertTrue(persisted.metadata().version().length() > 0);
    }

    @Test
    void createWithPreferredId() {
        final ExceptionalResponse<AetherPersisted<String>> response =
                store.create(error::set, alice, "body", "custom-id");

        assertTrue(response.wasNoError());
        assertEquals("custom-id", response.response().metadata().id());
    }

    @Test
    void createPreferredIdConflict() {
        store.create(error::set, alice, "a", "same");
        error.set(null);
        final ExceptionalResponse<AetherPersisted<String>> response =
                store.create(error::set, alice, "b", "same");

        assertTrue(response.wasError());
        assertFailure(AetherFailure.Conflict);
    }

    @Test
    void createBlankPreferredIdValidation() {
        final ExceptionalResponse<AetherPersisted<String>> response =
                store.create(error::set, alice, "body", "  ");

        assertTrue(response.wasError());
        assertFailure(AetherFailure.Validation);
    }

    @Test
    void readAndUpdateWithVersion() {
        final AetherPersisted<String> created =
                store.create(error::set, alice, "v1", "id-1").response();
        final String version = created.metadata().version();

        final AetherPersisted<String> read =
                store.read(error::set, alice, "id-1").response();
        assertEquals("v1", read.resource());

        final AetherPrincipal bob = AetherPrincipal.user("bob");
        final AetherPersisted<String> updated = store.update(
                error::set,
                bob,
                "id-1",
                "v2",
                version,
                UpdateOptions.defaults()).response();

        assertEquals("v2", updated.resource());
        assertEquals("alice", updated.metadata().createdBy());
        assertEquals("bob", updated.metadata().updatedBy());
        assertNotEquals(version, updated.metadata().version());
    }

    @Test
    void updateVersionMismatchConflict() {
        store.create(error::set, alice, "v1", "id-1");
        error.set(null);
        final ExceptionalResponse<AetherPersisted<String>> response = store.update(
                error::set,
                alice,
                "id-1",
                "v2",
                "wrong-version",
                UpdateOptions.defaults());

        assertTrue(response.wasError());
        assertFailure(AetherFailure.Conflict);
    }

    @Test
    void updateMissingNotFoundUnlessCreateIfAbsent() {
        final ExceptionalResponse<AetherPersisted<String>> missing = store.update(
                error::set,
                alice,
                "nope",
                "body",
                "any",
                UpdateOptions.defaults());
        assertTrue(missing.wasError());
        assertFailure(AetherFailure.NotFound);

        error.set(null);
        final ExceptionalResponse<AetherPersisted<String>> created = store.update(
                error::set,
                alice,
                "nope",
                "body",
                "any",
                UpdateOptions.createIfAbsent());
        assertTrue(created.wasNoError());
        assertEquals("nope", created.response().metadata().id());
    }

    @Test
    void deleteIsIdempotent() {
        store.create(error::set, alice, "x", "del-me");
        assertTrue(store.delete(error::set, alice, "del-me").wasNoError());
        assertTrue(store.delete(error::set, alice, "del-me").wasNoError());
        assertTrue(store.read(error::set, alice, "del-me").wasError());
        assertFailure(AetherFailure.NotFound);
    }

    private void assertFailure(final AetherFailure expected) {
        assertInstanceOf(AetherException.class, error.get());
        assertEquals(expected, ((AetherException) error.get()).failure());
    }
}
