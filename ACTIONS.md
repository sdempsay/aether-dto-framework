# Actions log

## 2026-07-14 — OSGi SCR needs generated *Store interfaces

- Requirement: codegen must emit `UserDtoStore extends AetherResourceStore<UserDto>` (and singleton variant) so Declarative Services can bind by concrete service type. Documented in PRD-updated; TODO T5a.

## 2026-07-14 — @Unique / @Singleton before FS provider

- Added `@Unique` (group default = field name) and `@Singleton` annotations.
- `UniqueConstraintModel` + `UniqueIndexTable`; in-memory resource store enforces uniqueness when given a record `Class`.
- Tests for single-field, composite unique, update/delete index lifecycle; singleton marker + store conflict.
- FS provider still deferred until unique/singleton path is solid.

## 2026-07-14 — tests.md enables JUnit via dempsay-parent

- Added `aether-api/src/test/resources/tests.md` so parent profile injects junit-jupiter (removed hand-declared test deps from pom).

## 2026-07-14 — CRUD interfaces + in-memory store (T6 slice 1)

- Added `aether-api` store ports: `AetherResourceStore`, `AetherSingletonStore`, envelope/metadata, `UpdateOptions`, `AetherAck`.
- `AetherFailure` (PascalCase + httpStatus), `AetherException`, `AetherResponses`; `AetherPrincipal`.
- Optional `AbstractAetherResourceStore`; `InMemoryAetherResourceStore` / `InMemoryAetherSingletonStore` for tests.
- Unit tests (12) green; no FS provider yet (later `aether-store-fs`).

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
