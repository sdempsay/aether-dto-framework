package org.aether.store.fs;

import java.time.Instant;

import com.google.gson.JsonElement;

/**
 * On-disk JSON envelope: metadata + resource payload.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
final class StoredDocument {
    private StoredMetadata metadata;
    private JsonElement resource;

    StoredDocument() {
    }

    StoredDocument(final StoredMetadata metadata, final JsonElement resource) {
        this.metadata = metadata;
        this.resource = resource;
    }

    StoredMetadata getMetadata() {
        return metadata;
    }

    void setMetadata(final StoredMetadata metadata) {
        this.metadata = metadata;
    }

    JsonElement getResource() {
        return resource;
    }

    void setResource(final JsonElement resource) {
        this.resource = resource;
    }

    /**
     * Wire format for {@link org.aether.store.AetherResourceMetadata}.
     */
    static final class StoredMetadata {
        private String id;
        private Instant createdAt;
        private Instant updatedAt;
        private String version;
        private String createdBy;
        private String updatedBy;

        StoredMetadata() {
        }

        StoredMetadata(
                final String id,
                final Instant createdAt,
                final Instant updatedAt,
                final String version,
                final String createdBy,
                final String updatedBy) {
            this.id = id;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.version = version;
            this.createdBy = createdBy;
            this.updatedBy = updatedBy;
        }

        String getId() {
            return id;
        }

        Instant getCreatedAt() {
            return createdAt;
        }

        Instant getUpdatedAt() {
            return updatedAt;
        }

        String getVersion() {
            return version;
        }

        String getCreatedBy() {
            return createdBy;
        }

        String getUpdatedBy() {
            return updatedBy;
        }
    }
}
