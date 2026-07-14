package org.aether.store.unique;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory secondary indexes for unique constraint groups.
 *
 * <p><strong>Concurrency:</strong> map operations use {@link ConcurrentHashMap},
 * but {@link #reindex} is release-then-claim (not one atomic step). Callers that
 * interleave reindex with document updates without external locking (see
 * {@link org.aether.store.memory.InMemoryAetherResourceStore}) can race. Fine for
 * typical single-threaded unit tests.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public final class UniqueIndexTable {
    private final ConcurrentMap<String, ConcurrentMap<String, String>> byGroup =
            new ConcurrentHashMap<>();

    /**
     * Attempts to claim all keys for a resource id.
     *
     * @param resourceId owning resource id
     * @param keys keys extracted from the resource
     * @return null if all claims succeeded; otherwise the conflicting group name
     */
    public String tryClaim(final String resourceId, final List<UniqueKey> keys) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(keys, "keys");
        final List<UniqueKey> claimed = new ArrayList<>();
        for (final UniqueKey key : keys) {
            final ConcurrentMap<String, String> index =
                    byGroup.computeIfAbsent(key.group(), ignored -> new ConcurrentHashMap<>());
            final String previous = index.putIfAbsent(key.indexToken(), resourceId);
            if (previous != null && !previous.equals(resourceId)) {
                release(resourceId, claimed);
                return key.group();
            }
            if (previous == null) {
                claimed.add(key);
            }
        }
        return null;
    }

    /**
     * Releases index entries that currently point at {@code resourceId}.
     *
     * @param resourceId owning resource id
     * @param keys keys to release
     */
    public void release(final String resourceId, final List<UniqueKey> keys) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(keys, "keys");
        for (final UniqueKey key : keys) {
            final ConcurrentMap<String, String> index = byGroup.get(key.group());
            if (index != null) {
                index.remove(key.indexToken(), resourceId);
            }
        }
    }

    /**
     * Replaces unique keys for an updated resource (release old, claim new).
     *
     * @param resourceId resource id
     * @param oldKeys previous keys
     * @param newKeys new keys
     * @return null on success; conflicting group name on failure (old keys restored)
     */
    public String reindex(
            final String resourceId,
            final List<UniqueKey> oldKeys,
            final List<UniqueKey> newKeys) {
        release(resourceId, oldKeys);
        final String conflict = tryClaim(resourceId, newKeys);
        if (conflict != null) {
            tryClaim(resourceId, oldKeys);
            return conflict;
        }
        return null;
    }
}
