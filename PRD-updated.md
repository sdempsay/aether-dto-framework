# PRD updates (learned requirements)

## High-level goal: testable without infrastructure

Applications written against Aether’s **API layer** (DTO annotations/builders, store ports, access-control SPI) must be **unit-testable without a running database or document store**.

- Depend on **interfaces/ports**, not on MongoDB, JDBC servers, cloud SDKs, etc.
- **Persistence providers** (`aether-store-fs`, later `aether-store-jdbc`, …) and **AAA** (e.g. allow-all for tests, real policy in prod) are **swappable**.
- Requiring infrastructure solely to run unit tests is an **anti-goal** (“nothing worse than being locked to a contract that needs a live MongoDB for tests”).
- Integration tests may use a real provider (e.g. temp-dir FS); unit tests should not have to.

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
    // Actor fields populated from AetherPrincipal on write (exact naming TBD):
    // String createdBy();  String updatedBy();
}
```

- **Only the store** writes metadata. Clients never supply trusted timestamps / version / actor fields.
- **`id`** is store-authoritative after create. Optional **preferred id** on multi-resource create is a *create input* overload, not client-authored metadata after the fact.
- **Principal on every method** supplies actor for `createdBy` / `updatedBy` (and AAA when a checking decorator is used).

#### Envelope

```java
public interface AetherPersisted<T> {
    AetherResourceMetadata metadata();
    T resource();   // pure domain @AetherRecord
}
```

Create / read / update return `ExceptionalResponse<AetherPersisted<T>>`.  
Delete returns `ExceptionalResponse<Void>` (or equivalent empty success).

#### API parameter order (mandatory for store / new multi-arg APIs)

1. **`ExceptionalListener onError` is always the first parameter.**
2. **`AetherPrincipal principal` is second** on all store methods (create/read/update/delete), including singleton — supports AAA **and** audit fields such as createdBy/updatedBy when present in metadata.
3. Remaining fixed parameters follow.
4. **Varargs**, if any, are always last.

Rationale: trailing `onError` fights future varargs overloads; leading `onError` stays stable when optional tails grow. Principal on every method avoids “store never sees actor” for metadata.

Note: existing `AetherBuilder.build(ExceptionalListener)` is already single-arg. New persistence ports follow the first-parameter rule even when there is no varargs yet.

ACL may still be enforced by a **decorating** store that checks then delegates; the **port** always threads principal so providers can record audit metadata.

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
 * CRUD store for a resource type {@code T}.
 * v1 key type is always String (UUID or preferred string id).
 * Semantics parallel HTTP collection resources.
 *
 * Parameter order: onError, principal, then operation args.
 */
public interface AetherResourceStore<T> {

    /** POST — store assigns random UUID string as id. */
    ExceptionalResponse<AetherPersisted<T>> create(
        ExceptionalListener onError,
        AetherPrincipal principal,
        T resource);

    /**
     * POST — use preferredId after validation (e.g. width/length);
     * conflict if that id already exists.
     */
    ExceptionalResponse<AetherPersisted<T>> create(
        ExceptionalListener onError,
        AetherPrincipal principal,
        T resource,
        String preferredId);

    /** GET by id — not found if missing. */
    ExceptionalResponse<AetherPersisted<T>> read(
        ExceptionalListener onError,
        AetherPrincipal principal,
        String id);

    /**
     * PUT — full replace of resource at id.
     * {@code expectedVersion} is required (from last read). Mismatch → conflict.
     * If missing and createIfAbsent: create at id; else not found.
     */
    ExceptionalResponse<AetherPersisted<T>> update(
        ExceptionalListener onError,
        AetherPrincipal principal,
        String id,
        T resource,
        String expectedVersion,
        UpdateOptions options);

    /**
     * DELETE — remove at id.
     * Idempotent: missing id → success.
     */
    ExceptionalResponse<Void> delete(
        ExceptionalListener onError,
        AetherPrincipal principal,
        String id);
}
```

Preferred id is a **second overload**, not `Optional`. Do **not** use null preferredId to mean “auto-generate.”

#### Optional `AbstractAetherResourceStore<T>` (in `aether-api`)

Providers **may** extend a skeletal base in the API module; they are **not** required to.

| Responsibility | Where |
|----------------|--------|
| Public `create(onError, principal, resource)` | Base: mint id via injectable `Supplier<String>` (default UUID), then forward |
| Public `create(..., preferredId)` | Base: reject null/blank → `Validation`; else `doCreate(...)` |
| Actual insert + metadata | Provider: `protected abstract doCreate(...)` (or fully custom impl of the interface) |

```java
// Sketch — providers choose extend-or-not
public abstract class AbstractAetherResourceStore<T> implements AetherResourceStore<T> {
    // final create overloads → doCreate
    protected abstract ExceptionalResponse<AetherPersisted<T>> doCreate(
        ExceptionalListener onError,
        AetherPrincipal principal,
        T resource,
        String id);
}
```

- In-memory / FS can extend the abstract class to avoid duplicating overload plumbing.  
- A provider that needs totally different create semantics implements `AetherResourceStore` directly.  
- Same idea optional later for `AbstractAetherSingletonStore` if useful.

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

### Failure taxonomy (HTTP-mappable)

`ExceptionalResponse` is a **record** and cannot be subclassed; failures stay `ExceptionalResponse.failure()` with detail on the **`ExceptionalListener`**. Aether therefore defines a **typed failure** the listener receives (and hosts can map to HTTP later).

#### `AetherFailure` (enum) + `AetherException` (or `AetherStoreException`)

```java
// Enum constants use PascalCase (not SCREAMING_SNAKE) — project preference.
// HTTP status is metadata on the enum (int only; no servlet/Jakarta types in aether-api).
public enum AetherFailure {
    Validation(400),  // domain/constraint problems (may wrap ValidationException)
    NotFound(404),    // missing resource; also unauthorized read/update/delete (hide existence)
    Conflict(409),    // duplicate id, unique violation, version mismatch, singleton already exists
    Identity(400),    // path id vs body id mismatch (if applicable)
    Forbidden(403),   // optional explicit deny (e.g. create when type not allowed); prefer NotFound for read paths
    Internal(500);    // backend I/O, unexpected

    private final int httpStatus;

    AetherFailure(final int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}

public class AetherException extends RuntimeException {
    private final AetherFailure failure;
    // failure(), message, optional cause
}
```

Host mapping: `ex.failure().httpStatus()` (optionally with reason phrase at the edge). Core stays free of HTTP framework types.

Helpers (illustrative):

```java
// onError first; returns ExceptionalResponse.failure() after notifying listener
AetherResponses.fail(onError, AetherFailure.NotFound, "UserDto/" + id);
```

| Situation | `AetherFailure` | Typical HTTP (host mapping) |
|-----------|-----------------|----------------------------|
| Domain validation failed | `Validation` | 400 |
| Resource missing | `NotFound` | 404 |
| Unauthorized read/update/delete (hide existence) | `NotFound` | 404 |
| Unauthorized create / explicit deny | `Forbidden` | 403 |
| Duplicate id / unique / version / singleton exists | `Conflict` | 409 |
| Path vs body identity mismatch | `Identity` | 400 |
| Backend I/O / unexpected | `Internal` | 500 |

**Host mapping:** use `failure.httpStatus()` at the servlet/Spring/MCP edge. Keep the enum stable; do **not** put Jakarta/servlet types inside `aether-api`—only the status **int** on the enum.

Validation may still use existing `ValidationException`; store layer should surface it as `AetherFailure.Validation` (wrap or translate) so one switch covers persistence-facing errors.

**Enum naming:** Aether public enums use **PascalCase** constants (`NotFound`, not `NOT_FOUND`). Apply the same style to other new enums in this project unless there is a strong interop reason not to.

Store code returns plain `ExceptionalResponse.success(...)` / `failure()`; it does **not** invent a subclass of `ExceptionalResponse`.

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

    ExceptionalResponse<AetherPersisted<T>> create(
        ExceptionalListener onError,
        AetherPrincipal principal,
        T resource);

    ExceptionalResponse<AetherPersisted<T>> read(
        ExceptionalListener onError,
        AetherPrincipal principal);

    ExceptionalResponse<AetherPersisted<T>> update(
        ExceptionalListener onError,
        AetherPrincipal principal,
        T resource,
        String expectedVersion,
        UpdateOptions options);

    ExceptionalResponse<Void> delete(
        ExceptionalListener onError,
        AetherPrincipal principal);
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

**Rule: one persistence provider per Maven module / JAR / (future) OSGi bundle.**  
Consumers depend only on the provider(s) they use. Providers must not drag unrelated backends onto the classpath. Access-control decorator may live in `aether-api` (SPI + checking wrapper) or a tiny shared module — not inside a single provider.

| Module | Artifact role | Contents |
|--------|---------------|----------|
| `aether-api` | Contracts | Annotations, builders API, `AetherResourceStore` / `AetherSingletonStore`, `AetherPersisted`, metadata, `@Unique` / `@Singleton`, persistence exceptions, `AetherPrincipal` / `AetherAccessControl` SPI + checking decorator (no I/O backend) |
| `aether-builder-gen` | Compile-time only | JSR-269 + FreeMarker; later unique-group / store helper codegen |
| `aether-runtime` | Thin consumer aggregator (current MVP) | Continues to re-export `aether-api` + exceptional for DTO users **without** requiring a store. Does **not** embed FS/JDBC implementations. |
| `aether-store-fs` | **Provider** | Filesystem JSON store(s) only; depends on `aether-api` (+ Gson or chosen JSON lib as needed) |
| `aether-store-jdbc` (later) | **Provider** | JDBC backend only; own transitive deps (driver not required if DataSource-injected) |
| `aether-store-<name>` (later) | **Provider** | Any additional backend — **new module**, same pattern |

```
aether/
├── aether-api/
├── aether-builder-gen/
├── aether-runtime/          # DTO runtime aggregator — not "all providers"
├── aether-store-fs/         # first persistence provider
└── aether-store-jdbc/       # example future provider
```

**Dependency direction**

```text
aether-store-*  →  aether-api
app             →  aether-api + aether-store-fs   (and/or other providers)
app             →  aether-runtime                (optional; DTOs only)
```

Providers implement the same interfaces; they never depend on each other. OSGi later: each `aether-store-*` is its own bundle with explicit package exports.

### Implementation slices (suggested)

1. **First PR — interfaces + in-memory fake** (`aether-api` and/or tiny test-facing module):  
   - `AetherResourceStore` / `AetherSingletonStore`, envelope, metadata (incl. actor fields as designed)  
   - `AetherFailure` / `AetherException` / helpers, `UpdateOptions`  
   - `AetherPrincipal` on all methods  
   - **In-memory** store implementation for unit tests (proves testability without infra)  
   - Not FS; not OAuth  
2. **New module `aether-store-fs`** — FS JSON multi-resource CRUD.  
3. **Unique indexes** for `@Unique` groups (in `aether-store-fs`).  
4. **Singleton** FS implementation.  
5. Checking decorator / `AetherAccessControl` if not already thin-stubbed.  
6. Docs: consumer deps (`aether-store-fs` vs `aether-runtime` vs in-memory for tests).  
7. Later: **`aether-store-jdbc`** as its own module.

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
| Provider packaging | **One module/JAR/bundle per persistence provider**; `aether-runtime` stays DTO aggregator only |
| Typed failures | `AetherFailure` (PascalCase + `httpStatus()` metadata) + `AetherException` on the listener |
| Principal | On **all** store methods (after `onError`); enables AAA + createdBy/updatedBy |
| Key type v1 | **String only** |
| Preferred id | **Second create overload**, not `Optional` |
| First PR | Interfaces + **in-memory fake store** (no FS yet) |
| Create overloads | Two public methods; optional `AbstractAetherResourceStore` in api (providers may extend) |

### Open for implementation detail (non-blocking)

- Exact on-disk JSON schema (metadata nested vs sibling keys).
- `K` as `String` only in v1 vs generic.
- Normalization for unique index keys (case folding, trimming)—default: exact string match unless annotated later.
- Locking strategy for FS index + document consistency under concurrent processes.

### OSGi SCR: generated store interfaces (required for DS)

OSGi Declarative Services (SCR) registers and injects services primarily by **type**. A generic port alone is awkward as a component service interface:

- `AetherResourceStore<UserDto>` is not a reifiable service type SCR can publish cleanly as “the User store.”
- Consumers need a **stable, named interface** to reference in `@Reference` / XML (`interface="…UserDtoStore"`).
- Implementations (FS, in-memory, JDBC) then **implement that generated interface**, which **extends** the generic Aether port.

**Target codegen (when store generation lands):**

```java
// For a multi-instance @AetherRecord
public interface UserDtoStore extends AetherResourceStore<UserDto> {}

// For @AetherRecord + @Singleton
public interface AppConfigDtoStore extends AetherSingletonStore<AppConfigDto> {}
```

| Piece | Role |
|-------|------|
| Generated `*Store` interface | Extends `AetherResourceStore<T>` or `AetherSingletonStore<T>`; SCR service type |
| Provider impl | `class FsUserDtoStore implements UserDtoStore` (or component class) |
| Generic Aether ports | Shared contract + default methods / abstract base remain on the parent |

**Implications**

- Builder codegen today only emits `*Builder` implementing `AetherBuilder` (+ view interfaces). **Store interface generation is a separate codegen track** (still FreeMarker / processor), required for OSGi-friendly apps even if pure Java SE can keep using the generic ports.
- Aligns with T5 (OSGi bundle packaging) and T6 (persistence): DS works when each resource type has a **dedicated service interface** extending the Aether store API.
- In-memory / FS modules can ship reference components that implement the generated interfaces once those interfaces exist.

**Not required for unit tests** that inject `AetherResourceStore<T>` directly; required for production OSGi SCR wiring.

### Wishlist (after filtering)

- Explore GraphQL and/or other **generic frontend query** approaches **once a filter/list query port exists** — reminder only: [issue #7](https://github.com/sdempsay/aether-dto-framework/issues/7) / TODO **T8**.
