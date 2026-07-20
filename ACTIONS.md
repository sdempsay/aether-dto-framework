# Actions log

## 2026-07-20 — AGENT-USAGE.md for consumers

- Added `AGENT-USAGE.md`: how agents consume Aether (coords, processor path, DTOs/builders/stores, exceptional, in-memory vs FS).
- Dedicated **OSGi** section: bundle matrix, install rules, SCR/`*Store`, Felix runtime deps, common mistakes.
- Linked from `README.md` and `agents.md` (maintain vs consume split).

## 2026-07-20 — T5: OSGi bundle packaging (dempsay-felix-parent)

- Reactor parent: `dempsay-parent` → `dempsay-felix-parent:1.1.0-SNAPSHOT` (bnd + provided OSGi APIs).
- `aether-api` / `aether-store-fs`: `felix.bundle.exportcontents` for public packages; verified Export-Package / BSN / Import-Package in MANIFEST.MF.
- `aether-runtime`: Maven aggregator only (no exports). `aether-builder-gen`: compile-time only (no exports).
- Documented OSGi consumer checklist in README; PRD §5 + PRD-updated. `code-review` skipped (tool unusable). Closes #4 packaging scope; T5b–T5d still open.

## 2026-07-20 — Correct Maven coordinates and Java packages

- Renamed `groupId` `org.aether` → `org.dempsay.aether` (aligned with dempsay namespace and `aether-test`).
- Renamed Java packages `org.aether.*` → `org.dempsay.aether.*` (sources, templates, processor FQN, docs).
- Bumped version `0.1.0-SNAPSHOT` → `1.1.0-SNAPSHOT`.
- Updated consumer `aether-test` dependency groupIds, imports, OSGi export/unpack includes, and `aether.version`.
- Artifact IDs unchanged (`aether`, `aether-api`, …). Historical `review-aether-*.md` left as-is.
- Javadoc `@since` tags set to `1.0.0` (API introduction baseline; project version remains `1.1.0-SNAPSHOT`).
- `code-review diff` skipped this session (tool hung / not usable); rename is mechanical.

## 2026-07-14 — Issues for @AetherStoreProviders server codegen (Option A)

- Opened #9 (annotation API), #8 (aether-store-gen adapters), #10 (optional SCR on generated providers).
- TODO T5b–T5d; PRD-updated server provider codegen section.

## 2026-07-14 — T5a: generated *Store interfaces for SCR

- Processor emits `{Record}Store` via `Store.java.ftl` for every `@AetherRecord`.
- Default: `extends AetherResourceStore<Record>`; with `@Singleton`: `extends AetherSingletonStore<Record>`.
- Tests: MyDtoStore resource port, ConfigDtoStore singleton port.

## 2026-07-14 — Exceptional compliance for store I/O

- Refactored FS `DocumentIo` and stores to `ExceptionalSupplier` / `ExceptionalResponse` (no `throws IOException` on our APIs).
- Unique key extraction returns `ExceptionalResponse`; lazy unique-index rebuild under store ops with listener.
- Gson `TypeAdapter` still declares `throws IOException` (library SPI). Principal blank-name remains `IllegalArgumentException` (programmer error).

## 2026-07-14 — Session wrap: review + push persistence stack

- Updated ACTIONS for completed T6 slices (api ports, unique/singleton, `aether-store-fs`).
- Ran `code-review diff --base origin/master` before push: Java agents **Clean**; summary **APPROVE_WITH_NITS**.
- pom-security / xml-formatter nits on `aether-store-fs/pom.xml` (SNAPSHOT reactor parent, versions from dependencyManagement, `standalone="no"`) match existing modules — **not** changed; same pattern as `aether-api`.
- Pushed local `master` to `origin` (GitHub).

## 2026-07-14 — aether-store-fs (Gson, fully synchronized)

- New module `aether-store-fs`: `FileSystemAetherResourceStore` / `FileSystemAetherSingletonStore`.
- Layout `{root}/{type}/{id}.json` and `_singleton.json`; envelope JSON via Gson 2.11.0.
- Store-wide lock for thread-safe prototype use; unique index rebuilt on open; atomic temp+move writes.
- User approved Gson as provider dependency.

## 2026-07-14 — OSGi SCR needs generated *Store interfaces

- Requirement: codegen must emit `UserDtoStore extends AetherResourceStore<UserDto>` (and singleton variant) so Declarative Services can bind by concrete service type. Documented in PRD-updated; TODO T5a.

## 2026-07-14 — @Unique / @Singleton (before FS provider)

- Added `@Unique` (group default = field name) and `@Singleton` annotations.
- `UniqueConstraintModel` + `UniqueIndexTable`; in-memory resource store enforces uniqueness when given a record `Class`.
- Tests for single-field, composite unique, update/delete index lifecycle; singleton marker + store conflict.
- Followed by `aether-store-fs` implementation (see above).

## 2026-07-14 — tests.md enables JUnit via dempsay-parent

- Added `aether-api/src/test/resources/tests.md` so parent profile injects junit-jupiter (removed hand-declared test deps from pom).

## 2026-07-14 — CRUD interfaces + in-memory store (T6 slice 1)

- Added `aether-api` store ports: `AetherResourceStore`, `AetherSingletonStore`, envelope/metadata, `UpdateOptions`, `AetherAck`.
- `AetherFailure` (PascalCase + httpStatus), `AetherException`, `AetherResponses`; `AetherPrincipal`.
- Optional `AbstractAetherResourceStore`; `InMemoryAetherResourceStore` / `InMemoryAetherSingletonStore` for tests.
- Unit tests green; FS provider landed later as `aether-store-fs`.

## 2026-07-14 — CRUD pre-flight API freezes

- Principal on all store methods (audit + AAA); String keys only; preferred id via create overload; first PR = interfaces + in-memory fake.
- Optional `AbstractAetherResourceStore` in `aether-api` (create overloads → `doCreate`); providers may extend or implement the interface directly.

## 2026-07-14 — AetherFailure for HTTP-mappable errors

- Documented `AetherFailure` enum + `AetherException` on the exceptional listener path (no ExceptionalResponse subclass). Host maps enum → HTTP status later.
- Enum constants are PascalCase (`NotFound`, not `NOT_FOUND`) per project preference.
- Each constant carries `httpStatus` int metadata via enum constructor (`NotFound(404)`, `.httpStatus()`).

## 2026-07-14 — Store API: ExceptionalListener first

- Convention: `onError` is always the first parameter on store/new multi-arg APIs (varargs last). Documented in `PRD-updated.md`.

## 2026-07-14 — High-level goal: testable without infrastructure

- Called out in `PRD.md` (executive summary, core principles, anti-goals), `PRD-updated.md`, `README.md`, `AGENTS.md`: app unit tests must not require a live DB/document server; ports + swappable providers/AAA.

## 2026-07-14 — One module per persistence provider

- Locked: each store backend is its own Maven module/JAR (`aether-store-fs`, later `aether-store-jdbc`, …); `aether-runtime` does not embed providers. Documented in `PRD-updated.md` / `PRD.md`.

## 2026-07-14 — Wishlist issue: query/GraphQL after filtering

- Opened [issue #7](https://github.com/sdempsay/aether-dto-framework/issues/7); TODO **T8** (`wishlist`) — revisit GraphQL / generic frontend query once filters exist.

## 2026-07-14 — Persistence layer design (T6)

- Wrote full CRUD/HTTP-shaped persistence design to `PRD-updated.md` (metadata envelope, version UUID, `@Unique` groups, `@Singleton`, FS JSON layout).
- Updated GitHub issue #5 acceptance criteria and key decisions to match.

## 2026-07-08 — Require `code-review diff` before complete

- Documented in `AGENTS.md` / `PRD-updated.md`: run `code-review diff` on the change set before shipping/PRs.

## 2026-07-08 — Seed post-MVP GitHub issues

- Opened issues #1–#6 (nested DTOs, collections, custom annotations, OSGi, runtime persistence, type-level validation).
- Wired `TODO.md` rows T2–T7 to issue links.

## 2026-07-08 — Hybrid backlog (GitHub Issues + TODO.md)

- Mirrored review-pipeline backlog convention in `AGENTS.md` / `TODO.md` / `PRD-updated.md`.
- Repo: `sdempsay/aether-dto-framework`; use `gh issue view` / PRs with `Fixes #N`.

## 2026-07-08 — FreeMarker `*.java.ftl` convention

- Renamed `Builder.ftl` → `Builder.java.ftl` and `validation.ftl` → `validation.java.ftl`.
- Updated `BuilderCodegen` template load path and `#include` directives.
- Documented convention in `PRD.md` and `PRD-updated.md`.
