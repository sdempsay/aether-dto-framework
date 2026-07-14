package org.aether.store.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.aether.access.AetherPrincipal;
import org.aether.annotations.Unique;
import org.aether.failure.AetherException;
import org.aether.failure.AetherFailure;
import org.aether.store.AetherPersisted;
import org.aether.store.UpdateOptions;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestFileSystemAetherResourceStore {
    @TempDir
    private Path root;

    private FileSystemAetherResourceStore<FsUser> store;
    private AetherPrincipal alice;
    private AtomicReference<Exception> error;

    @BeforeEach
    void setUp() {
        store = new FileSystemAetherResourceStore<>(root, FsUser.class);
        alice = AetherPrincipal.user("alice");
        error = new AtomicReference<>();
    }

    @Test
    void createReadUpdateDeleteRoundTrip() {
        final AetherPersisted<FsUser> created =
                store.create(error::set, alice, new FsUser("bob", "Bob"), "u1").response();
        assertEquals("u1", created.metadata().id());
        assertEquals("bob", created.resource().username());

        final AetherPersisted<FsUser> read = store.read(error::set, alice, "u1").response();
        assertEquals("Bob", read.resource().displayName());

        final AetherPersisted<FsUser> updated = store.update(
                error::set,
                alice,
                "u1",
                new FsUser("bob", "Bobby"),
                read.metadata().version(),
                UpdateOptions.defaults()).response();
        assertEquals("Bobby", updated.resource().displayName());

        assertTrue(store.delete(error::set, alice, "u1").wasNoError());
        assertTrue(store.read(error::set, alice, "u1").wasError());
        assertFailure(AetherFailure.NotFound);
    }

    @Test
    void uniqueUsernameConflict() {
        store.create(error::set, alice, new FsUser("bob", "B"), "u1");
        error.set(null);
        final ExceptionalResponse<AetherPersisted<FsUser>> response =
                store.create(error::set, alice, new FsUser("bob", "Other"), "u2");
        assertTrue(response.wasError());
        assertFailure(AetherFailure.Conflict);
    }

    @Test
    void rebuildsUniqueIndexOnReopen() {
        store.create(error::set, alice, new FsUser("bob", "B"), "u1");
        final FileSystemAetherResourceStore<FsUser> reopened =
                new FileSystemAetherResourceStore<>(root, FsUser.class);
        error.set(null);
        final ExceptionalResponse<AetherPersisted<FsUser>> response =
                reopened.create(error::set, alice, new FsUser("bob", "X"), "u2");
        assertTrue(response.wasError());
        assertFailure(AetherFailure.Conflict);
    }

    @Test
    void rejectsUnsafeIds() {
        final ExceptionalResponse<AetherPersisted<FsUser>> response =
                store.create(error::set, alice, new FsUser("x", "X"), "../evil");
        assertTrue(response.wasError());
        assertFailure(AetherFailure.Validation);
    }

    private void assertFailure(final AetherFailure expected) {
        assertInstanceOf(AetherException.class, error.get());
        assertEquals(expected, ((AetherException) error.get()).failure());
    }

    record FsUser(@Unique String username, String displayName) {
    }
}
