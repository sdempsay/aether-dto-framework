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
| T5 | OSGi bundle packaging for aether modules | complete | [#4](https://github.com/sdempsay/aether-dto-framework/issues/4) — dempsay-felix-parent + exportcontents; SCR providers remain T5b–T5d |
| T5a | Codegen: `*Store` interfaces extending `AetherResourceStore` / `AetherSingletonStore` | complete | `Store.java.ftl`; `@Singleton` → `AetherSingletonStore`, else `AetherResourceStore` |
| T5b | Define `@AetherStoreProviders` annotation API (Option A) | complete | [#9](https://github.com/sdempsay/aether-dto-framework/issues/9) — `aether-store-gen` |
| T5c | Server store codegen: `@AetherStoreProviders` → Fs/Memory `*Store` adapters | complete | [#8](https://github.com/sdempsay/aether-dto-framework/issues/8) — processor in `aether-store-gen` |
| T5d | Optional SCR `@Component` on generated provider classes | complete | [#10](https://github.com/sdempsay/aether-dto-framework/issues/10) — `scr = true` on `@AetherStoreProviders` |
| T6 | Persistence layer: CRUD stores, metadata envelope, FS JSON (design in PRD-updated) | in_progress | [#5](https://github.com/sdempsay/aether-dto-framework/issues/5) — remaining: AAA decorator, polish; SCR store interfaces done (T5a) |
| T7 | Type-level validation annotations (deferred from MVP design) | pending | [#6](https://github.com/sdempsay/aether-dto-framework/issues/6) |
| T8 | Wishlist: GraphQL / generic frontend query after list/filter exists | wishlist | [#7](https://github.com/sdempsay/aether-dto-framework/issues/7) — after T9 / filtering; not basic list |
| T9 | Store read-all / list (unfiltered enumeration on `AetherResourceStore`) | complete | [#11](https://github.com/sdempsay/aether-dto-framework/issues/11) — `list(onError, principal)` |
