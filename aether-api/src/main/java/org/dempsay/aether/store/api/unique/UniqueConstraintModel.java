package org.dempsay.aether.store.api.unique;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.dempsay.aether.annotations.Unique;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Describes {@link Unique} groups on a record type and extracts index keys from
 * instances.
 *
 * <p>Uses record component accessors (not field reflection). Intended for
 * in-memory and other providers until codegen emits extractors.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class UniqueConstraintModel {
    private static final UniqueConstraintModel EMPTY = new UniqueConstraintModel(List.of());

    private final List<Group> groups;

    private UniqueConstraintModel(final List<Group> groups) {
        this.groups = List.copyOf(groups);
    }

    /**
     * Returns an empty model (no uniqueness checks).
     *
     * @return empty model
     */
    public static UniqueConstraintModel empty() {
        return EMPTY;
    }

    /**
     * Builds a model from {@link Unique} annotations on a record type.
     *
     * @param type resource class; non-records yield an empty model
     * @return model for that type
     */
    public static UniqueConstraintModel forType(final Class<?> type) {
        Objects.requireNonNull(type, "type");
        if (!type.isRecord()) {
            return EMPTY;
        }

        final Map<String, List<Accessor>> byGroup = new LinkedHashMap<>();
        for (final RecordComponent component : type.getRecordComponents()) {
            final Unique unique = component.getAnnotation(Unique.class);
            if (unique == null) {
                continue;
            }
            final String groupName = unique.group() == null || unique.group().isBlank()
                    ? component.getName()
                    : unique.group();
            final Method accessor = component.getAccessor();
            accessor.setAccessible(true);
            byGroup.computeIfAbsent(groupName, ignored -> new ArrayList<>())
                    .add(new Accessor(component.getName(), accessor));
        }

        final List<Group> groups = new ArrayList<>();
        byGroup.forEach((name, accessors) -> groups.add(new Group(name, List.copyOf(accessors))));
        return groups.isEmpty() ? EMPTY : new UniqueConstraintModel(groups);
    }

    /**
     * Returns whether any unique groups are defined.
     *
     * @return true if uniqueness is configured
     */
    public boolean isEmpty() {
        return groups.isEmpty();
    }

    /**
     * Extracts unique keys for indexing. Groups with any null member are omitted
     * (multiple nulls are allowed across documents).
     *
     * @param resource domain instance
     * @return keys to claim or release, or failure if an accessor cannot be read
     */
    public ExceptionalResponse<List<UniqueKey>> keysOf(final Object resource) {
        Objects.requireNonNull(resource, "resource");
        if (groups.isEmpty()) {
            return ExceptionalResponse.success(List.of());
        }
        return ExceptionalSupplier.of(() -> extractKeys(resource)).execute();
    }

    private List<UniqueKey> extractKeys(final Object resource) throws ReflectiveOperationException {
        final List<UniqueKey> keys = new ArrayList<>();
        for (final Group group : groups) {
            final List<String> values = new ArrayList<>(group.accessors().size());
            boolean skip = false;
            for (final Accessor accessor : group.accessors()) {
                // Wrap in Optional so null field values are not ExceptionalResponse.success(null)
                final Optional<Object> raw = Optional.ofNullable(accessor.method().invoke(resource));
                if (raw.isEmpty()) {
                    skip = true;
                    break;
                }
                values.add(String.valueOf(raw.get()));
            }
            if (!skip) {
                keys.add(new UniqueKey(group.name(), values));
            }
        }
        return keys;
    }

    private record Group(String name, List<Accessor> accessors) {
    }

    private record Accessor(String componentName, Method method) {
    }
}
