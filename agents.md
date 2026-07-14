# Agent Notes

Guidance for AI agents working in this repository. Global rules in `~/.grok/AGENTS.md` also apply; where they conflict on VCS, **this repo uses GitHub**.

## VCS

- Remote: [sdempsay/aether-dto-framework](https://github.com/sdempsay/aether-dto-framework)
- Use `gh` (not `glab`) for issues and PRs
- Prefer PRs over direct-only workflows when shipping backlog items linked to issues

## Backlog (TODO.md + GitHub Issues)

Hybrid tracker (same pattern as review-pipeline):

- **`TODO.md`** — thin index: task ID, status, issue link (offline-friendly)
- **GitHub Issues** — acceptance criteria, design, discussion ([open issues](https://github.com/sdempsay/aether-dto-framework/issues))
- **`ACTIONS.md`** — work log when tasks ship

**When starting work:** read TODO row → `gh issue view N` for full context (when network available).

**When shipping:** PR with `Fixes #N` → issue closes → mark TODO `complete` → one line in `ACTIONS.md`.

**When adding work:** open a GitHub issue (body = acceptance criteria) → add a TODO row with status `pending` and the issue link. Do not put long design notes only in `TODO.md`.

## Session start

- Read `TODO.md` for task status
- For pending tasks with issue links, read the issue (`gh issue view N`) for acceptance criteria
- Read `PRD.md` and `PRD-updated.md` for requirements
- Read `~/.grok/rules/maven.md` (this project has `pom.xml`)

## Exceptional error handling

Aether uses [exceptional-java](https://github.com/sdempsay/exceptional-java) for explicit failure paths. See [WhyBeExceptional.md](https://github.com/sdempsay/exceptional-java/blob/master/WhyBeExceptional.md) and `PRD.md` Section 4 for rationale.

**Production and main-source code** (`aether-api`, `aether-builder-gen/src/main`, generated builder templates):

- I/O and other failure-prone work returns `ExceptionalResponse<T>` or is executed through `ExceptionalSupplier`, `ExceptionalResource`, or `ExceptionalResourceAction`.
- Do not add `throws` to method signatures for failure paths that should be exceptional.
- Checked exceptions may appear only inside lambdas passed to exceptional utilities (the library owns the catch).

**Tests are an intentional exception.** `throws` on JUnit test methods and test helpers is fine and expected. Tests often use reflection, `javax.tools` compilation, temp directories, and other APIs that declare checked exceptions. Do not refactor test code to be fully exceptional unless there is a concrete benefit. Prefer `ExceptionalResource` / `ExceptionalSupplier` in test utilities only when it improves clarity, not as a blanket rule.

When reviewing or fixing exceptional compliance, scope checks to `src/main` and generated templates — not `src/test`.

## Module layout

| Module | Role |
|--------|------|
| `aether-api` | Public annotations, `AetherBuilder`, `ValidationException` |
| `aether-builder-gen` | JSR-269 processor and FreeMarker templates |
| `aether-runtime` | Consumer-facing dependency aggregator |

## Other conventions

- Java 21+, flat record DTOs only for MVP (no collections or nested records).
- Generated builders implement `AetherBuilder<T>` and record view interfaces when compatible.
- Run `mvn test` from the repo root to verify changes.