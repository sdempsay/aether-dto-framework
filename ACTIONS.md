# Actions log

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
