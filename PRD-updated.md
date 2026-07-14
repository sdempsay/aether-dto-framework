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
