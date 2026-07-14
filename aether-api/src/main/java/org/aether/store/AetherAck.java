package org.aether.store;

/**
 * Non-null success token for store operations with no payload (e.g. delete).
 *
 * <p>{@link org.dempsay.utils.exceptional.api.ExceptionalResponse#success} treats a
 * null body as failure, so delete returns {@code ExceptionalResponse<AetherAck>}
 * with {@link #INSTANCE}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public final class AetherAck {
    /** Shared success token. */
    public static final AetherAck INSTANCE = new AetherAck();

    private AetherAck() {
    }
}
