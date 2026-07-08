# Agent Notes

Guidance for AI agents working in this repository.

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