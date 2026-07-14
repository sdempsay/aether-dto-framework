package org.aether.access;

import java.util.Objects;

/**
 * Caller identity for store operations (AAA and audit metadata such as
 * createdBy / updatedBy).
 *
 * <p>OAuth scopes and roles are host concerns; this type carries stable
 * identity for the request. Effective grants may be a subset of the account's
 * full rights when mapped at the edge.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public interface AetherPrincipal {
    /**
     * Returns a stable name for this principal (user id, client id, etc.).
     *
     * @return non-null identity string
     */
    String name();

    /**
     * Creates an end-user principal.
     *
     * @param name user id or subject; must not be blank
     * @return principal
     */
    static AetherPrincipal user(final String name) {
        return new SimplePrincipal("user", requireName(name));
    }

    /**
     * Creates a service / client principal (e.g. OAuth client credentials).
     *
     * @param name service or client id; must not be blank
     * @return principal
     */
    static AetherPrincipal service(final String name) {
        return new SimplePrincipal("service", requireName(name));
    }

    /**
     * Returns the well-known runtime / system principal for internal actors.
     *
     * @return system principal
     */
    static AetherPrincipal system() {
        return SimplePrincipal.SYSTEM;
    }

    private static String requireName(final String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }

    /**
     * Simple principal with a kind prefix for display.
     */
    final class SimplePrincipal implements AetherPrincipal {
        private static final AetherPrincipal SYSTEM = new SimplePrincipal("system", "system");

        private final String kind;
        private final String name;

        private SimplePrincipal(final String kind, final String name) {
            this.kind = kind;
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        /**
         * Returns the principal kind ({@code user}, {@code service}, {@code system}).
         *
         * @return kind label
         */
        public String kind() {
            return kind;
        }

        @Override
        public String toString() {
            return kind + ':' + name;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SimplePrincipal simple)) {
                return false;
            }
            return kind.equals(simple.kind) && name.equals(simple.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, name);
        }
    }
}
