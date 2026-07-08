# PRD updates (learned requirements)

## FreeMarker template naming

- FreeMarker templates that generate Java source **must** use the extension `*.java.ftl` (e.g. `Builder.java.ftl`, `validation.java.ftl`).
- Rationale: makes generated-language intent obvious in editors and tooling; FreeMarker still treats the full filename as the template name.
- Template includes and `Configuration.getTemplate(...)` calls must use the full name including `.java.ftl`.
