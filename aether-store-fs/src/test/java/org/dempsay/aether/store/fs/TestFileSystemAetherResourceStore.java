package org.dempsay.aether.store.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.dempsay.aether.access.AetherPrincipal;
import org.dempsay.aether.annotations.Unique;
import org.dempsay.aether.failure.AetherException;
import org.dempsay.aether.failure.AetherFailure;
import org.dempsay.aether.store.AetherPersisted;
import org.dempsay.aether.store.UpdateOptions;
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

    @Test
    void listEmptyStoreSucceedsWithEmptyList() {
        final ExceptionalResponse<List<AetherPersisted<FsUser>>> response = store.list(error::set, alice);

        assertTrue(response.wasNoError());
        assertTrue(response.response().isEmpty());
    }

    @Test
    void listReturnsAllSortedById() {
        store.create(error::set, alice, new FsUser("bob", "B"), "u-b");
        store.create(error::set, alice, new FsUser("alice", "A"), "u-a");
        store.create(error::set, alice, new FsUser("carol", "C"), "u-c");

        final ExceptionalResponse<List<AetherPersisted<FsUser>>> response = store.list(error::set, alice);

        assertTrue(response.wasNoError());
        final List<AetherPersisted<FsUser>> all = response.response();
        assertEquals(3, all.size());
        assertEquals("u-a", all.get(0).metadata().id());
        assertEquals("u-b", all.get(1).metadata().id());
        assertEquals("u-c", all.get(2).metadata().id());
        assertEquals("alice", all.get(0).resource().username());
    }

    private void assertFailure(final AetherFailure expected) {
        assertInstanceOf(AetherException.class, error.get());
        assertEquals(expected, ((AetherException) error.get()).failure());
    }

    record FsUser(@Unique String username, String displayName) {
    }
}
