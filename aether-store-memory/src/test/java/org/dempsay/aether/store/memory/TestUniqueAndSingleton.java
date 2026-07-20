package org.dempsay.aether.store.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.dempsay.aether.api.access.AetherPrincipal;
import org.dempsay.aether.api.annotations.Singleton;
import org.dempsay.aether.api.failure.AetherException;
import org.dempsay.aether.api.failure.AetherFailure;
import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.aether.api.store.UpdateOptions;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestUniqueAndSingleton {
    private InMemoryAetherResourceStore<UniqueUserDto> store;
    private AetherPrincipal alice;
    private AtomicReference<Exception> error;

    @BeforeEach
    void setUp() {
        store = new InMemoryAetherResourceStore<>(UniqueUserDto.class);
        alice = AetherPrincipal.user("alice");
        error = new AtomicReference<>();
    }

    @Test
    void duplicateUsernameConflicts() {
        store.create(error::set, alice, user("alice", "t1", "a@ex.com"), "u1");
        error.set(null);
        final ExceptionalResponse<AetherPersisted<UniqueUserDto>> response =
                store.create(error::set, alice, user("alice", "t2", "b@ex.com"), "u2");
        assertTrue(response.wasError());
        assertFailure(AetherFailure.Conflict);
    }

    @Test
    void compositeTenantEmailConflicts() {
        store.create(error::set, alice, user("bob", "acme", "bob@acme.com"), "u1");
        error.set(null);
        final ExceptionalResponse<AetherPersisted<UniqueUserDto>> response =
                store.create(error::set, alice, user("carol", "acme", "bob@acme.com"), "u2");
        assertTrue(response.wasError());
        assertFailure(AetherFailure.Conflict);
    }

    @Test
    void sameEmailDifferentTenantAllowed() {
        assertTrue(store.create(error::set, alice, user("bob", "acme", "x@ex.com"), "u1")
                .wasNoError());
        assertTrue(store.create(error::set, alice, user("carol", "other", "x@ex.com"), "u2")
                .wasNoError());
    }

    @Test
    void updateCanChangeUniqueFieldsWhenFree() {
        final AetherPersisted<UniqueUserDto> created =
                store.create(error::set, alice, user("bob", "acme", "b@ex.com"), "u1").response();
        final ExceptionalResponse<AetherPersisted<UniqueUserDto>> updated = store.update(
                error::set,
                alice,
                "u1",
                user("bobby", "acme", "b@ex.com"),
                created.metadata().version(),
                UpdateOptions.defaults());
        assertTrue(updated.wasNoError());
        assertEquals("bobby", updated.response().resource().username());
    }

    @Test
    void updateUniqueConflictLeavesOldValues() {
        store.create(error::set, alice, user("bob", "acme", "b@ex.com"), "u1");
        store.create(error::set, alice, user("carol", "acme", "c@ex.com"), "u2");
        final String version = store.read(error::set, alice, "u2").response().metadata().version();
        error.set(null);
        final ExceptionalResponse<AetherPersisted<UniqueUserDto>> response = store.update(
                error::set,
                alice,
                "u2",
                user("carol", "acme", "b@ex.com"),
                version,
                UpdateOptions.defaults());
        assertTrue(response.wasError());
        assertFailure(AetherFailure.Conflict);
        assertEquals("c@ex.com", store.read(error::set, alice, "u2").response().resource().email());
    }

    @Test
    void deleteReleasesUniqueKeys() {
        store.create(error::set, alice, user("bob", "acme", "b@ex.com"), "u1");
        assertTrue(store.delete(error::set, alice, "u1").wasNoError());
        assertTrue(store.create(error::set, alice, user("bob", "acme", "b@ex.com"), "u2")
                .wasNoError());
    }

    @Test
    void singletonAnnotationPresent() {
        assertTrue(AppConfigDto.class.isAnnotationPresent(Singleton.class));
        final InMemoryAetherSingletonStore<AppConfigDto> singleton = new InMemoryAetherSingletonStore<>();
        assertTrue(singleton.create(error::set, alice, new AppConfigDto("dark")).wasNoError());
        error.set(null);
        assertTrue(singleton.create(error::set, alice, new AppConfigDto("light")).wasError());
        assertFailure(AetherFailure.Conflict);
    }

    private static UniqueUserDto user(
            final String username,
            final String tenant,
            final String email) {
        return new UniqueUserDto(username, tenant, email, username);
    }

    private void assertFailure(final AetherFailure expected) {
        assertInstanceOf(AetherException.class, error.get());
        assertEquals(expected, ((AetherException) error.get()).failure());
    }

    @Singleton
    private record AppConfigDto(String theme) {
    }
}
