package org.dempsay.aether.store.memory;

import org.dempsay.aether.api.annotations.AetherRecord;
import org.dempsay.aether.api.annotations.Unique;

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
