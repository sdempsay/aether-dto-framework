package org.aether.store.unique;

import java.util.List;
import java.util.Objects;

/**
 * One unique-constraint index entry: group name plus ordered field values.
 *
 * @param group constraint group (field name or explicit group)
 * @param values ordered string forms of the group members; empty if skipped
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public record UniqueKey(String group, List<String> values) {
    /**
     * Creates a unique key.
     *
     * @param group group name
     * @param values ordered values
     */
    public UniqueKey {
        Objects.requireNonNull(group, "group");
        values = List.copyOf(Objects.requireNonNull(values, "values"));
    }

    /**
     * Returns a stable map key for secondary indexes.
     *
     * @return encoded index key
     */
    public String indexToken() {
        return String.join("\u001f", values);
    }
}
