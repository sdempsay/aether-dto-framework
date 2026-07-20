# Agent Notes

Guidance for AI agents working in this repository. Global rules in `~/.grok/AGENTS.md` also apply; where they conflict on VCS, **this repo uses GitHub**.

**Consuming Aether from another project** (Maven deps, processors, OSGi): see **[AGENT-USAGE.md](AGENT-USAGE.md)** — not this file.

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

**When shipping:** run code review (below) → PR with `Fixes #N` → issue closes → mark TODO `complete` → one line in `ACTIONS.md`.

**When adding work:** open a GitHub issue (body = acceptance criteria) → add a TODO row with status `pending` and the issue link. Do not put long design notes only in `TODO.md`.

## Code review (`code-review diff`)

Before a change set is considered complete (and before opening/updating a PR), run the local review pipeline on the diff:

```bash
code-review diff
```

Useful variants:

| Goal | Command |
|------|---------|
| Uncommitted / working-tree changes (default) | `code-review diff` |
| Against a base branch/commit | `code-review diff --base origin/master` |
| Staged only | `code-review diff --staged` |
| Save full report | `code-review diff --output review-output.md` |
| Classify only (no LLM) | `code-review diff --dry-run` |

Expectations:

- Prefer addressing **must-fix** / high-severity findings before ship; note intentional deferrals in `ACTIONS.md` or the PR.
- Use `--no-chat` in non-interactive agent sessions (default is already no chat unless `--chat`).
- Tool is on PATH as `code-review` (install from review-pipeline / `~/tools/code-review`). If missing, say so and fall back to a careful manual review rather than inventing results.

## Session start

- Read `TODO.md` for task status
- For pending tasks with issue links, read the issue (`gh issue view N`) for acceptance criteria
- Read `PRD.md` and `PRD-updated.md` for requirements
- Read `~/.grok/rules/maven.md` (this project has `pom.xml`)

## Exceptional error handling

Aether uses [exceptional-java](https://github.com/sdempsay/exceptional-java) for explicit failure paths. See [WhyBeExceptional.md](https://github.com/sdempsay/exceptional-java/blob/master/WhyBeExceptional.md) and `PRD.md` Section 4 for rationale.

**Production and main-source code** (`aether-api`, `aether-builder-gen/src/main`, `aether-store-fs`, generated builder templates):

- I/O and other failure-prone work returns `ExceptionalResponse<T>` or is executed through `ExceptionalSupplier`, `ExceptionalResource`, or `ExceptionalResourceAction`.
- Do not add `throws` to method signatures for failure paths that should be exceptional — **including private/package-private helpers** (`DocumentIo`, `readPersisted`, `writePersisted`, etc.). No adapter boundary: public `ExceptionalResponse` ports do not excuse `throws` or hand-written `try/catch` on filesystem helpers in the same module.
- Checked exceptions may appear only inside lambdas passed to exceptional utilities (the library owns the catch).
- When `code-review` flags exceptional violations on `aether-store-fs`, fix them with exceptional utilities end-to-end; do not document carve-outs for internal I/O.

**Tests are an intentional exception.** `throws` on JUnit test methods and test helpers is fine and expected. Tests often use reflection, `javax.tools` compilation, temp directories, and other APIs that declare checked exceptions. Do not refactor test code to be fully exceptional unless there is a concrete benefit. Prefer `ExceptionalResource` / `ExceptionalSupplier` in test utilities only when it improves clarity, not as a blanket rule.

When reviewing or fixing exceptional compliance, scope checks to `src/main` and generated templates — not `src/test`.

## Module layout

| Module | Role |
|--------|------|
| `aether-api` | Annotations, builders, store ports, in-memory fakes, `ValidationException` (OSGi exports) |
| `aether-builder-gen` | JSR-269 processor: builders + `*Store` interfaces (compile-time only; not for Felix install) |
| `aether-runtime` | Consumer-facing Maven dependency aggregator (no OSGi exports) |
| `aether-store-fs` | Filesystem JSON provider (Gson); OSGi exports `org.dempsay.aether.store.fs` |

Reactor parent: `dempsay-felix-parent`. Public packages via `felix.bundle.exportcontents`. See README OSGi section and `../dempsay-felix-parent/AGENT-USAGE.md`.

## Other conventions

- Java 21+, flat record DTOs only for MVP (no collections or nested records).
- Generated builders implement `AetherBuilder<T>` and record view interfaces when compatible.
- **Generated code must pass dempsay checkstyle** (templates in `aether-builder-gen`). Consumer projects may ignore generated sources; aether still emits clean Java. See PRD-updated “Generated code must pass checkstyle”.
- Run `mvn test` from the repo root to verify changes.
- **Testability goal:** keep app-facing surface on API ports so consumers can unit-test with fakes (in-memory store, allow-all AAA) — never design so unit tests require a live DB/document server.
- **JUnit:** dempsay-parent enables `junit-jupiter` when `src/test/resources/tests.md` exists — do not hand-add junit deps to module POMs; add that file instead.
