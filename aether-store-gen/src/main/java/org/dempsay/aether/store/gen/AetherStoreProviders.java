package org.dempsay.aether.store.gen;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which {@code @AetherRecord} types should receive generated persistence
 * <strong>provider adapters</strong> (filesystem and/or in-memory) on the
 * <strong>server</strong> (or impl) module.
 *
 * <p><strong>Placement:</strong> put this on a server {@code package-info.java} or a
 * dedicated server marker type — <em>not</em> on DTO records in an api module. That
 * keeps {@code aether-api} free of {@code aether-store-fs} / provider dependencies
 * and matches OSGi api-vs-impl layouts.
 *
 * <p><strong>Example (server package):</strong>
 * <pre>
 * {@literal @}AetherStoreProviders(
 *     filesystem = { MyDto.class, OrderDto.class },
 *     singletonFilesystem = { AppConfigDto.class },
 *     memory = { MyDto.class }
 * )
 * package com.example.product.server.stores;
 *
 * import org.dempsay.aether.store.gen.AetherStoreProviders;
 * import com.example.product.api.AppConfigDto;
 * import com.example.product.api.MyDto;
 * import com.example.product.api.OrderDto;
 * </pre>
 *
 * <p>Expected generated adapters (T5c / {@code aether-store-gen} processor), names
 * illustrative:
 * <ul>
 *   <li>{@code FsMyDtoStore extends FileSystemAetherResourceStore<MyDto>
 *       implements MyDtoStore}</li>
 *   <li>{@code FsAppConfigDtoStore extends FileSystemAetherSingletonStore<AppConfigDto>
 *       implements AppConfigDtoStore}</li>
 *   <li>{@code MemoryMyDtoStore extends InMemoryAetherResourceStore<MyDto>
 *       implements MyDtoStore}</li>
 * </ul>
 *
 * <p><strong>Rules (enforced by the store-gen processor when present):</strong>
 * <ul>
 *   <li>Every listed type must be a record annotated with {@code @AetherRecord}.</li>
 *   <li>{@link #singletonFilesystem()} members should also be {@code @Singleton};
 *       multi-resource types belong in {@link #filesystem()} or {@link #memory()}.</li>
 *   <li>At least one of the three arrays must be non-empty.</li>
 *   <li>Empty defaults are valid only while lists are still being filled; a fully
 *       empty annotation is rejected by the processor.</li>
 * </ul>
 *
 * <p>When {@link #scr()} is {@code true}, generated adapters include OSGi DS
 * {@code @Component(service = XStore.class)} and {@code @Activate} (server module
 * must depend on OSGi component annotations; keep api free of OSGi).
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 * @see org.dempsay.aether.api.annotations.AetherRecord
 * @see org.dempsay.aether.api.annotations.Singleton
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ ElementType.PACKAGE, ElementType.TYPE })
public @interface AetherStoreProviders {
    /**
     * Multi-instance domain types backed by the filesystem provider
     * ({@code FileSystemAetherResourceStore}).
     *
     * @return record classes (default empty)
     */
    Class<?>[] filesystem() default {};

    /**
     * Singleton domain types backed by the filesystem singleton provider
     * ({@code FileSystemAetherSingletonStore}). Prefer types marked
     * {@code @Singleton}.
     *
     * @return record classes (default empty)
     */
    Class<?>[] singletonFilesystem() default {};

    /**
     * Multi-instance (or test) types backed by the in-memory provider
     * ({@code InMemoryAetherResourceStore}).
     *
     * @return record classes (default empty)
     */
    Class<?>[] memory() default {};

    /**
     * When {@code true}, emit OSGi Declarative Services annotations on each
     * generated adapter ({@code @Component}, {@code @Activate}). Default
     * {@code false} for non-OSGi / pure unit-test servers.
     *
     * <p>Filesystem adapters activated via SCR take {@code @Reference FileStoreConfig}
     * and use {@code location()} as the storage root. Memory adapters use a no-arg
     * activate ctor.
     *
     * @return whether to generate SCR annotations
     */
    boolean scr() default false;
}
