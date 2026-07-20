package org.dempsay.aether.store.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.dempsay.aether.api.access.AetherPrincipal;
import org.dempsay.aether.api.annotations.Singleton;
import org.dempsay.aether.api.failure.AetherFailure;
import org.dempsay.aether.api.failure.AetherException;
import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.aether.api.store.UpdateOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TestFileSystemAetherSingletonStore {
    @TempDir
    private Path root;

    private FileSystemAetherSingletonStore<Config> store;
    private AetherPrincipal system;
    private AtomicReference<Exception> error;

    @BeforeEach
    void setUp() {
        store = new FileSystemAetherSingletonStore<>(root, Config.class);
        system = AetherPrincipal.system();
        error = new AtomicReference<>();
    }

    @Test
    void singletonLifecycle() {
        final AetherPersisted<Config> created =
                store.create(error::set, system, new Config("dark")).response();
        assertEquals(FileSystemAetherSingletonStore.SINGLETON_ID, created.metadata().id());
        assertEquals("dark", store.read(error::set, system).response().resource().theme());

        final String version = created.metadata().version();
        store.update(error::set, system, new Config("light"), version, UpdateOptions.defaults());
        assertEquals("light", store.read(error::set, system).response().resource().theme());

        assertTrue(store.create(error::set, system, new Config("again")).wasError());
        assertInstanceOf(AetherException.class, error.get());
        assertEquals(AetherFailure.Conflict, ((AetherException) error.get()).failure());

        assertTrue(store.delete(error::set, system).wasNoError());
        assertTrue(store.read(error::set, system).wasError());
    }

    @Singleton
    record Config(String theme) {
    }
}
