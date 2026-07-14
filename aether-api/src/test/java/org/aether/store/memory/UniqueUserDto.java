package org.aether.store.memory;

import org.aether.annotations.AetherRecord;
import org.aether.annotations.Unique;

/**
 * Test record with single-field and composite uniqueness.
 */
@AetherRecord
public record UniqueUserDto(
        @Unique String username,
        @Unique(group = "tenantEmail") String tenantId,
        @Unique(group = "tenantEmail") String email,
        String displayName) {
}
