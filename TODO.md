# TODO.md

Task tracker for Aether (`PRD.md`).

**Backlog sync:** Pending work is tracked on [GitHub Issues](https://github.com/sdempsay/aether-dto-framework/issues). This file is a thin index (ID, status, issue link) for offline agent session start. Details and discussion live on the issue. When shipping: `Fixes #N` in PR → close issue → mark row `complete` here.

## Completed / in-repo work

| ID | Task | Status | Issue |
|----|------|--------|-------|
| T1 | Rename FreeMarker templates that generate Java to `*.java.ftl` and update references | complete | — |

## Future / backlog (post-MVP)

| ID | Task | Status | Issue |
|----|------|--------|-------|
| T2 | Nested `@AetherRecord` components in generated builders | pending | [#1](https://github.com/sdempsay/aether-dto-framework/issues/1) |
| T3 | Collection components (`List` and related) on `@AetherRecord` DTOs | pending | [#2](https://github.com/sdempsay/aether-dto-framework/issues/2) |
| T4 | Extensible / custom validation annotations beyond MVP set | pending | [#3](https://github.com/sdempsay/aether-dto-framework/issues/3) |
| T5 | OSGi bundle packaging for aether modules | pending | [#4](https://github.com/sdempsay/aether-dto-framework/issues/4) |
| T6 | Persistence layer: CRUD stores, metadata envelope, FS JSON (design in PRD-updated) | in_progress | [#5](https://github.com/sdempsay/aether-dto-framework/issues/5) — api ports + in-memory done; FS provider next |
| T7 | Type-level validation annotations (deferred from MVP design) | pending | [#6](https://github.com/sdempsay/aether-dto-framework/issues/6) |
| T8 | Wishlist: GraphQL / generic frontend query after filtering exists | wishlist | [#7](https://github.com/sdempsay/aether-dto-framework/issues/7) |
