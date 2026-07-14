# PRD updates (learned requirements)

## FreeMarker template naming

- FreeMarker templates that generate Java source **must** use the extension `*.java.ftl` (e.g. `Builder.java.ftl`, `validation.java.ftl`).
- Rationale: makes generated-language intent obvious in editors and tooling; FreeMarker still treats the full filename as the template name.
- Template includes and `Configuration.getTemplate(...)` calls must use the full name including `.java.ftl`.

## Backlog tracking (GitHub Issues + TODO.md)

Mirror of review-pipeline hybrid tracker:

- **Source of truth for pending work:** [GitHub Issues](https://github.com/sdempsay/aether-dto-framework/issues) (acceptance criteria, design, discussion).
- **`TODO.md`:** thin offline index (ID, status, issue link only).
- **`ACTIONS.md`:** ship log.
- **Start work:** TODO row → `gh issue view N`.
- **Ship work:** PR with `Fixes #N` → mark TODO complete → log in `ACTIONS.md`.
- This repo is on **GitHub** (`sdempsay/aether-dto-framework`); use `gh`, not `glab`.

## Code review before complete

- Agents should run **`code-review diff`** (from the review-pipeline CLI) on the change set before treating work as complete or opening/updating a PR.
- Typical: `code-review diff` or `code-review diff --base origin/master`; optional `--output <file.md>`.
- Address must-fix / high-severity findings; document intentional deferrals.

---

## Persistence layer design (T6 / issue #5)

Design status: **agreed direction** (not yet implemented). Tracks [issue #5](https://github.com/sdempsay/aether-dto-framework/issues/5).

### Goals

- Persist validated `@AetherRecord` DTOs through a **CRUD** API whose semantics parallel **HTTP resource operations** (POST/GET/PUT/DELETE).
- Keep domain records **pure** (immutable values); system fields live in a **metadata envelope**.
- **No runtime reflection** over DTO fields; mapping and constraints are explicit or generated.
- Failures use **exceptional-java** (`ExceptionalResponse` + `ExceptionalListener`), consistent with `AetherBuilder`.
- First backend: **filesystem JSON documents** (Gson-friendly raw JSON). JDBC/other backends later behind the same interfaces.

### Non-goals (v1)

- Full HTTP server / REST framework
- JPA/Hibernate entity lifecycle, sessions, dirty checking
- Collection **list/query** on the core store (deferred; leave room for a later query port—possibly frontend-friendly generic querying)
- Field-level merge / PATCH (update is full document replace)
- Auto-generated Gson `JsonAdapter` (still out of scope per original PRD)

### HTTP → store verb mapping

| HTTP | Verb | Multi-resource store | Singleton store |
|------|------|----------------------|-----------------|
| **POST** | Create | `create(...)` | `create(resource)` |
| **GET** | Read | `read(id)` | `read()` |
| **PUT** | Update | `update(id, resource, expectedVersion, options)` | `update(resource, expectedVersion, options)` |
| **DELETE** | Delete | `delete(id)` | `delete()` |

No **upsert** as a separate verb. Update may take an optional **create-if-absent** flag (default **off**).

### Core types

#### Metadata (store-owned)

Common interface for all persisted resources:

```java
public interface AetherResourceMetadata {
    String id();
    Instant createdAt();
    Instant updatedAt();
    String version();   // opaque etag; see Versioning
}
```

- **Only the store** writes metadata. Clients never supply trusted `createdAt` / `updatedAt` / `version`.
- **`id`** is store-authoritative after create. Optional **preferred id** on multi-resource create is a *create input*, not client-authored metadata after the fact.

#### Envelope

```java
public interface AetherPersisted<T> {
    AetherResourceMetadata metadata();
    T resource();   // pure domain @AetherRecord
}
```

Create / read / update return `ExceptionalResponse<AetherPersisted<T>>`.  
Delete returns `ExceptionalResponse<Void>` (or equivalent empty success).

#### Update options

```java
public final class UpdateOptions {
    private final boolean createIfAbsent;
    // factory: UpdateOptions.defaults() → createIfAbsent false
    // factory: UpdateOptions.createIfAbsent()
}
```

### Multi-resource store (collection)

```java
/**
 * CRUD store for a resource type {@code T} with key {@code K} (typically String id).
 * Semantics parallel HTTP collection resources.
 */
public interface AetherResourceStore<T, K> {

    /**
     * POST — create.
     * If preferredId is empty/absent: store assigns a random UUID string as id.
     * If preferredId is present: store validates (e.g. string width/length) and uses it;
     * fails with conflict if that id already exists.
     */
    ExceptionalResponse<AetherPersisted<T>> create(
        T resource,
        Optional<K> preferredId,
        ExceptionalListener onError);

    /** GET by id — not found if missing. */
    ExceptionalResponse<AetherPersisted<T>> read(K id, ExceptionalListener onError);

    /**
     * PUT — full replace of resource at id.
     * {@code expectedVersion} is required (from last read). Mismatch → conflict.
     * If missing and createIfAbsent: create at id; else not found.
     * Path id is authoritative; if resource later embeds id, mismatch → identity error.
     */
    ExceptionalResponse<AetherPersisted<T>> update(
        K id,
        T resource,
        String expectedVersion,
        UpdateOptions options,
        ExceptionalListener onError);

    /**
     * DELETE — remove at id.
     * Idempotent: missing id → success.
     */
    ExceptionalResponse<Void> delete(K id, ExceptionalListener onError);
}
```

Convenience overloads (e.g. `create(T, onError)` → no preferred id) are fine.

### Identity policy (multi-resource create)

| Preference | Behavior |
|------------|----------|
| **Default** | Store generates a **random UUID** (string form) as `metadata.id` |
| **Allowed** | Caller may pass a **string key** as preferred id within configured/annotated width constraints (same channel as `@Id` / string id type) |

Rationale: UUID-by-default is safe; optional natural/string keys remain available when useful.

### Versioning / etag (optimistic concurrency)

- `metadata.version` is an **opaque random UUID** minted by the store on **every successful create and update**.
- Not a sequence number; not necessarily a content hash. Ordering of revisions uses **`updatedAt`**, not version strings.
- **Update requires `expectedVersion`** equal to the version from the client’s last read.
- Match → apply body, set `updatedAt = now`, set `version = new random UUID`, return new envelope.
- Mismatch → **conflict** (no write). Prevents silent lost updates under full-document PUT.
- Concurrent edits to *different fields* still collide at the **document** level (no field merge in v1). Clients re-read, merge, retry with the new version.

### Delete and update edge cases

| Situation | Behavior |
|-----------|----------|
| Delete, id missing | **Success** (idempotent) |
| Update, id missing | **Not found** by default |
| Update, id missing, `createIfAbsent` | **Create** at that id (initial timestamps + version) |
| Create, id already exists | **Conflict** |
| Update, version mismatch | **Conflict** |

### Failure taxonomy (HTTP-inspired)

Delivered via `ExceptionalListener` / failure `ExceptionalResponse` (names illustrative):

| Situation | ~HTTP | Type (proposed) |
|-----------|-------|-----------------|
| Domain validation failed before I/O | 400 | existing `ValidationException` |
| Resource not found | 404 | `ResourceNotFoundException` |
| Id exists on create / unique constraint / version mismatch | 409 | `ResourceConflictException` |
| Path id vs body identity mismatch (if applicable) | 400 | `ResourceIdentityException` |
| Backend I/O failure | 500 | wrapped in exceptional failure path |

Validation remains the builder’s job when constructing `T`; the store assumes `T` is a valid domain value (or re-validates if we later accept builders).

### Field uniqueness

Cross-document constraints, **store-enforced** (not fully checkable in `build()` alone).

**Declaration:** field-level only, optional group name:

```java
@AetherRecord
public record UserDto(
    @Unique                           // group defaults to field name "username"
    @MinLength(3) @MaxLength(50)
    String username,

    @Unique(group = "tenantEmail")
    String tenantId,

    @Unique(group = "tenantEmail")
    String email,

    String displayName
) {}
```

| Rule | Meaning |
|------|---------|
| `@Unique` without `group` | **group = field name** → single-field uniqueness |
| Same `group` on multiple fields | **Composite** uniqueness on those fields together |
| Different groups | Independent constraints |

On create/update, store maintains secondary indexes (FS: under type root). Violation → **conflict**.  
Update that changes unique fields must release old index entries and claim new ones atomically with the document write as far as the backend allows.

**Note:** uniqueness is not a substitute for **singleton cardinality** (see below).

### Singleton resources

First-class type-level annotation:

```java
@AetherRecord
@Singleton
public record AppConfigDto(
    @MinLength(1) String theme,
    boolean darkMode
) {}
```

- Cardinality **≤ 1** for the type.
- **No id on the public API** (not a collection resource).

```java
/**
 * CRUD for a type with at most one instance.
 * HTTP analogy: a single resource URL (e.g. /config), not a collection.
 */
public interface AetherSingletonStore<T> {

    ExceptionalResponse<AetherPersisted<T>> create(T resource, ExceptionalListener onError);

    ExceptionalResponse<AetherPersisted<T>> read(ExceptionalListener onError);

    ExceptionalResponse<AetherPersisted<T>> update(
        T resource,
        String expectedVersion,
        UpdateOptions options,
        ExceptionalListener onError);

    ExceptionalResponse<Void> delete(ExceptionalListener onError);
}
```

| Op | Behavior |
|----|----------|
| create | Fails with **conflict** if instance already exists |
| read | **Not found** if absent |
| update | Required `expectedVersion`; not found unless `createIfAbsent` |
| delete | **Idempotent** success if absent |

`metadata.id` may be a store-internal constant (e.g. `"_singleton"`) so the common metadata interface stays uniform; callers never pass id.

Processor should reject nonsensical combinations (e.g. treating a `@Singleton` type as a multi-id collection store). Field `@Unique` on a singleton type is largely redundant (0 or 1 row) but harmless.

### First backend: filesystem JSON

| Item | Choice |
|------|--------|
| Format | One **raw JSON** document per resource (domain body + metadata envelope serialized together, exact JSON shape TBD in implementation) |
| Multi-resource path | `{root}/{resourceType}/{id}.json` |
| Singleton path | `{root}/{resourceType}/_singleton.json` (or equivalent single well-known file) |
| Unique indexes | e.g. `{root}/{resourceType}/_unique/{group}/{encodedValues}` → id (create-exclusive / atomic replace as available) |

Root directory is configuration (constructor / config object), not a global singleton static.

### Module placement (target)

| Module | Contents |
|--------|----------|
| `aether-api` | `AetherResourceStore`, `AetherSingletonStore`, `AetherPersisted`, `AetherResourceMetadata`, `UpdateOptions`, `@Unique`, `@Singleton`, persistence exceptions; optional `@Id` if domain-side id documentation is needed |
| `aether-runtime` | Filesystem JSON reference implementation; later other backends |
| `aether-builder-gen` | Later: emit unique-group metadata, store helpers, key extraction without reflection |

### Implementation slices (suggested)

1. **API contracts** in `aether-api` (interfaces, metadata, annotations, exceptions).
2. **FS JSON** `AetherResourceStore` for flat DTOs (UUID id + preferred string id, version checks, idempotent delete).
3. **Unique indexes** for `@Unique` groups.
4. **`@Singleton` + `AetherSingletonStore`** FS implementation.
5. Tests for concurrency conflict, createIfAbsent, idempotent delete, unique violations.
6. Document consumer usage in README; keep JDBC as a later backend behind the same interfaces.

### Key decisions (summary)

| Decision | Choice |
|----------|--------|
| Verb model | HTTP-like CRUD: create / read / update / delete |
| Domain vs system fields | Envelope `AetherPersisted<T>` + `AetherResourceMetadata` |
| Metadata authority | Store only |
| Create id | Random UUID default; optional client string preferred id |
| Update missing | Error; optional `createIfAbsent` |
| Delete missing | Idempotent success |
| Version on update | **Required** expected version |
| Version format | New **random UUID** each successful write |
| List/query | Not on core interface in v1 |
| Uniqueness | `@Unique` on fields; default group = field name; same group = composite |
| Singleton | `@Singleton` + `AetherSingletonStore` (no caller id) |
| First backend | Filesystem, one JSON file per id under `{root}/{type}/` |

### Open for implementation detail (non-blocking)

- Exact on-disk JSON schema (metadata nested vs sibling keys).
- `K` as `String` only in v1 vs generic.
- Normalization for unique index keys (case folding, trimming)—default: exact string match unless annotated later.
- Locking strategy for FS index + document consistency under concurrent processes.
