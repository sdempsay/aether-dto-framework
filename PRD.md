Perfect — here is your **Preliminary PRD for *Aether Persistence Framework*** in Markdown format.

You can copy/paste this into a `.md` file, commit to Git, or convert to PDF later.

---

# 🧭 Preliminary PRD: Aether Persistence Framework
**Version**: 0.1 (Draft)
**Date**: 2026-02-18
**Author**: Senior Architect (reporting to Principal Architect)
**Target Java Version**: Java 21

---

## 🔍 Executive Summary

Aether is a minimal, annotation-driven, **compile-time–generated persistence DTO layer** for Java 21+. It leverages records for immutability and JSR-269 code generation to produce validated, type-safe builders — with zero runtime reflection. Designed from day one for eventual OSGi deployment (no blocking dependencies), Aether prioritizes simplicity, safety, and performance.

> ✅ **MVP Scope**: Flat DTOs only (no nesting/collections), Gson-compatible serialization (manual use), `@AetherRecord`, `@Nullable`, `@MinLength`, `@MaxLength`, `@RegexMatch`.

---

## 🎯 Core Principles

| Principle | Rationale |
|---------|-----------|
| **Immutable-by-default** | Records enforce immutability; builders produce fully initialized DTOs. |
| **Compile-time validation & safety** | Generated builders embed checks (null, length, regex) — fail fast at build time. |
| **Zero runtime reflection** | All code is generated ahead of time → optimal performance + OSGi-compatible. |
| **No external runtime dependencies beyond spec’d artifacts** | Runtime: `exceptional-java` only (via `aether-runtime`). Compile-time: `jsr269-utilities` + FreeMarker in `aether-builder-gen`. |
| **Be Exceptional** | Prefer [exceptional-java](https://github.com/sdempsay/exceptional-java) over `try/catch` for failure paths — in generated builders, processor code, and consumer code. See [WhyBeExceptional.md](https://github.com/sdempsay/exceptional-java/blob/master/WhyBeExceptional.md). |

---

## 📦 Project Structure (Maven Modules)

```
aether/
├── pom.xml                       # Aggregator: modules + dependencyManagement (+ pluginManagement)
├── aether-api/                   # Public API: annotations, ValidationException
│   └── pom.xml (parent: aether)
├── aether-builder-gen/           # JSR-269 annotation processor (compile-time only)
│   ├── pom.xml (parent: aether; no inline versions)
│   └── src/main/resources/templates/   # FreeMarker builder templates (*.java.ftl)
├── aether-runtime/               # MVP: thin runtime dependency aggregator (api + exceptional); not store backends
│   └── pom.xml (parent: aether; no inline versions)
└── aether-store-*/               # Post-MVP: one Maven module/JAR per persistence provider (e.g. aether-store-fs)
    └── pom.xml (parent: aether; no inline versions)
```

Persistence providers are **separate artifacts** (see `PRD-updated.md`): e.g. `aether-store-fs`, later `aether-store-jdbc`. Do not put multiple backends in `aether-runtime` or a single “kitchen sink” store JAR.

### Dependency management

All external dependency **versions are declared once** in the root [`pom.xml`](pom.xml) under `<dependencyManagement>`. Child modules reference artifacts by `groupId` + `artifactId` only (plus `scope` where needed); they **must not** repeat version numbers.

Internal reactor modules (`aether-api`, `aether-builder-gen`, `aether-runtime`) are also listed in `dependencyManagement` at `${project.version}` so cross-module references stay consistent.

Version properties (e.g. `exceptional.version`, `freemarker.version`) live in the root `<properties>` section and are referenced from `dependencyManagement` — single place to bump a library.

Annotation processor paths (`maven-compiler-plugin` / `annotationProcessorPaths`) should use the same managed coordinates (no duplicate versions in child POMs).

#### Root `dependencyManagement` (target)

| Artifact | Version property | Used by |
|--------|------------------|---------|
| `org.aether:aether-api` | `${project.version}` | `aether-builder-gen`, `aether-runtime` |
| `org.aether:aether-builder-gen` | `${project.version}` | Consumer `annotationProcessorPaths` (documented in README) |
| `org.aether:aether-runtime` | `${project.version}` | Consumers |
| `org.dempsay.support.jsr269:jsr269-utilities` | `${jsr269-utilities.version}` | `aether-builder-gen` (compile + processor path) |
| `org.freemarker:freemarker` | `${freemarker.version}` | `aether-builder-gen` (compile) |
| `org.dempsay.utils:exceptional` | `${exceptional.version}` | `aether-builder-gen` (provided), `aether-runtime` (compile) |

Parent POM: `org.dempsay.maven:dempsay-parent:1.0.4` (inherits plugin versions; not duplicated in Aether BOM).

### Dependencies

| Artifact | Use | Scope |
|--------|------|-------|
| `org.dempsay.maven/dempsay-parent/1.0.4` | Maven parent POM | — |
| `org.dempsay.support.jsr269/jsr269-utilities` | Processor registration (`@Jsr269Processor`) | `annotationProcessor` only |
| `org.freemarker/freemarker` | Builder source generation templates | Compile-time only (`aether-builder-gen`) |
| `org.dempsay.utils/exceptional` | Functional error handling (`ExceptionalResponse<T>`, `ExceptionalSupplier`, `ExceptionalAction`) | Runtime (via `aether-runtime`); compile-time in `aether-builder-gen` |

---

## 📝 Section 1: DTO Definition

### Format
- Use **records** as the canonical DTO definition.
- Annotate with **`@AetherRecord`** to opt in to builder generation.
- Builder is **generated**, not hand-written.
- Validation annotations are placed on **record components** (not the record type).

#### Example DTO
```java
// Flat, non-nested, no collections in MVP
@AetherRecord
public record MyDto(
    @MinLength(3)
    @MaxLength(50)
    @RegexMatch(pattern = "^[a-zA-Z0-9_]+$")
    String data
) {}

// Nullable field example
@AetherRecord
public record SafeDto(@Nullable String notes) {}
```

> Records **without** `@AetherRecord` do not receive a generated builder.

### Supported Annotations (MVP)

| Annotation | Target | Behavior |
|-----------|--------|----------|
| `@AetherRecord` | Record type (`ElementType.TYPE`) | Opt-in marker; annotation processor generates `{Name}Builder` only for annotated flat records. |
| `@Nullable` | Record component | Field may be `null`; no validation applied. Absence implies non-null. |
| `@MinLength(int)` | String fields only | Enforces `String.length() >= value`. |
| `@MaxLength(int)` | String fields only | Enforces `String.length() <= value`. |
| `@RegexMatch(String pattern)` | String fields only | Enforces `Pattern.matches(pattern, field) == true`. |

> ⚠️ **Constraints**:
> - Only `String`, primitives (`int`, `boolean`, etc.), and their wrappers supported in MVP.
> - No nested records or collections (e.g., `List<String>`).

---

## ⚙️ Section 2: Builder Code Generation

### Tooling
- Uses [`jsr269-utilities`](https://github.com/sdempsay/jsr269-utilities) (`org.dempsay.support.jsr269/jsr269-utilities/1.0.1`) to:
  - Register the annotation processor via `@Jsr269Processor`.
  - Discover `@AetherRecord` records and their component annotations.
- Uses **FreeMarker** (`org.freemarker:freemarker:2.3.34`) to render builder source from templates:
  - Templates live in `aether-builder-gen/src/main/resources/templates/` (e.g. `Builder.java.ftl`).
  - The processor builds a template model (`RecordComponentModel` list, package name, record name) and writes output via `Filer`.
  - FreeMarker is a **compile-time-only** dependency; it is not on the consumer classpath.

### Generated Builder API

For this record:
```java
@AetherRecord
public record MyDto(
    @MinLength(3)
    @MaxLength(50)
    String data
) {}
```

The processor generates:

```java
// aether-builder-gen outputs this to target/generated-sources/annotations/
public final class MyDtoBuilder implements AetherBuilder<MyDto> {
    private String data;

    public MyDtoBuilder() {}

    // Copy constructor (defensive copy not required: builder is mutable, DTOs are immutable)
    public MyDtoBuilder(MyDto source) {
        if (source != null) {
            this.data = source.data();
        }
    }

    // Record-style accessor (read current builder state)
    public String data() {
        return data;
    }

    // Fluent mutator (write builder state)
    public MyDtoBuilder data(String data) {
        this.data = data;
        return this;
    }

    // Validation + build
    @Override
    public ExceptionalResponse<MyDto> build(ExceptionalListener onError) {
        var errors = new ArrayList<String>();

        if (data == null) {
            errors.add("Field 'data' must not be null");
        } else {
            int len = data.length();
            if (len < 3 || len > 50) {
                errors.add("Field 'data' length must be between 3 and 50, got " + len);
            }
        }

        if (!errors.isEmpty()) {
            onError.accept(new ValidationException(errors));
            return ExceptionalResponse.failure();
        }

        return ExceptionalResponse.success(new MyDto(data));
    }
}
```

Each record component produces **two** methods on the builder:

| Method | Returns | Role |
|--------|---------|------|
| `field()` | component type | Record-style accessor over in-progress builder state |
| `field(T value)` | `{Record}Builder` | Fluent mutator |

### Record-implemented interfaces

When a record `implements` one or more interfaces whose abstract accessor methods map to record components (same name, no parameters, compatible return type), the generated builder **also implements those interfaces**. The builder satisfies the interface via the generated `field()` accessors.

Example:

```java
interface Named {
    String name();
}

@AetherRecord
public record NamedDto(@MinLength(1) String name) implements Named {}

// Generated:
public final class NamedDtoBuilder implements AetherBuilder<NamedDto>, Named {
    public String name() { return name; }           // implements Named
    public NamedDtoBuilder name(String name) { ... } // fluent mutator
}
```

> A builder may be a **partial** `Named` before `build()` — fields can be null or fail validation. Callers should treat `build()` as the boundary that produces a validated DTO.

### Key Design Choices

| Feature | Implementation |
|--------|----------------|
| **Opt-in generation** | `@AetherRecord` marker required; unmarked records are ignored. |
| **Null safety** | Non-null by default; `@Nullable` suppresses null check. |
| **Reusability** | Builder is mutable and reusable (no cloning). |
| **Accessor + mutator** | Each component gets `field()` and `field(T)` (overload pair). |
| **Interface mirroring** | Builder `implements` the same compatible interfaces as the record. |
| **Error reporting** | Collects all validation errors into a single `ValidationException`. |
| **No reflection** | All checks are static string/concrete logic in generated code. |
| **Template-driven codegen** | Builder shape is defined in FreeMarker templates, not inline `StringBuilder` code. |

---

## 📦 Section 3: Serialization & Integration

### Gson Compatibility
- DTO records serialize cleanly with Gson *out-of-the-box* (no extra code needed).
- Example usage:
  ```java
  var gson = new Gson();
  var response = new MyDtoBuilder().data("hello").build(err -> logger.error("{}", err));
  if (response.wasNoError()) {
    MyDto dto = response.safeResponse().orElseThrow();
    String json = gson.toJson(dto); // → {"data":"hello"}
  }
  ```

### Not in MVP (Deferred)
| Feature | Reason |
|--------|--------|
| Auto-generated `JsonAdapter<MyDto>` | Requires runtime reflection (Gson limitation) — out of scope. |
| XML support (`@XmlElement`, etc.) | Focus on JSON + simplicity. |
| Direct DB binding (JDBC/Hibernate) | Future work in `aether-runtime`. |

### Persistence layer (post-MVP design)

Agreed design for resource CRUD, metadata envelope, uniqueness, singleton types, and a filesystem JSON first backend is recorded in **`PRD-updated.md`** (section *Persistence layer design*) and tracked as [issue #5](https://github.com/sdempsay/aether-dto-framework/issues/5) / TODO **T6**. Summary: HTTP-like `create`/`read`/`update`/`delete`, store-owned `AetherResourceMetadata` (id, timestamps, random-UUID version etag required on update), `@Unique` field groups, `@Singleton` + id-free store API, first implementation as `{root}/{type}/{id}.json`. **One Maven module/JAR (future OSGi bundle) per persistence provider** (`aether-store-fs`, …); `aether-runtime` remains a DTO dependency aggregator only.

---

## 🧪 Section 4: Validation & Error Handling

Aether uses [exceptional-java](https://github.com/sdempsay/exceptional-java) for all explicit failure handling. Read [WhyBeExceptional.md](https://github.com/sdempsay/exceptional-java/blob/master/WhyBeExceptional.md) for the rationale: validation and I/O failures are usually non-recoverable, so they should be acknowledged via `ExceptionalResponse` rather than scattered `try/catch` blocks.

**Project rule**: Unless there is a specific reason to use `try/catch` (e.g. inside `ExceptionalSupplier.of(() -> ...)` where the library owns the catch), Aether code uses `ExceptionalSupplier`, `ExceptionalAction`, and `ExceptionalResponse`.

### Exception Types
- `ValidationException` in `aether-api` (`org.aether.validation.ValidationException`, extends `RuntimeException`)
  → Contains list of error messages via `getErrors()` returning `List<String>`.

### Builder Contract
```java
// Always returns valid DTO or failure
ExceptionalResponse<MyDto> response = new MyDtoBuilder()
    .data("hello")
    .build(err -> logger.error("Validation failed", err));

if (response.wasNoError()) {
  MyDto dto = response.response();
  // Safe to use dto
} else {
  // ValidationException was delivered to listener.accept(...)
}
```

`ExceptionalListener` extends `Consumer<Exception>`, so lambdas and `accept(...)` are both valid in generated builders.

### Processor code (internal)
```java
ExceptionalSupplier.of(() -> BuilderCodegen.render(packageName, recordName, components))
    .with(e -> error(record, "Failed to generate builder: " + e.getMessage()))
    .execute();
```

> ✅ `ExceptionalResponse` provides `success()`, `failure()`, `wasError()`, `wasNoError()`, `safeResponse()`, `map()`, `then()`, `chain()`, and `stream()`. See [WhyBeExceptional.md](https://github.com/sdempsay/exceptional-java/blob/master/WhyBeExceptional.md).

---

## 🧩 Section 5: OSGi Readiness

### Design Principles
- ✅ No runtime reflection → works in OSGi.
- ✅ Modular Maven layout → each module can become a bundle later.
- ✅ No static state or singletons (avoids classloader issues).

### Action Required Later
- Add `Export-Package`, `Import-Package` headers when converting to bundles.

---

## 📋 Design Decisions

Resolved during planning (2026-07):

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Builder trigger | `@AetherRecord` marker | Explicit opt-in; avoids generating builders for every record |
| Annotation placement | `RECORD_COMPONENT` | Matches JSR 269 record model; type-level constraints deferred |
| Maven parent | `dempsay-parent:1.0.4` | Aligns with org standard |
| Codegen | FreeMarker templates via `Filer` | Readable, maintainable templates; compile-time-only dep |
| Builder accessors | `field()` + `field(T)` per component | Matches record contract; enables builder `implements` on record interfaces |
| Dependency versions | Root `dependencyManagement` only | One version per artifact across all submodules |
| Error handling | exceptional-java everywhere | No hand-rolled `try/catch` except inside Exceptional wrappers |
| `aether-runtime` MVP | Thin dependency aggregator | Pulls `aether-api` + `exceptional` for consumers; persistence deferred |

---

## 🚫 Anti-goals (Reaffirmed)

| Goal | Status |
|------|--------|
| No external frameworks beyond listed deps | ✅ Compliant (FreeMarker is compile-time only in `aether-builder-gen`) |
| No runtime reflection or proxying | ✅ Compliant via codegen |
| OSGi support deferred but design must not prevent future bundle conversion | ✅ Modular structure ensures this |

---

## 📌 Next Steps

1. **Sync PRD** with planning decisions (complete).
2. **Scaffold Maven modules** (`aether-api`, `aether-builder-gen`, `aether-runtime`) (complete).
3. **Implement JSR-269 processor** (`aether-builder-gen`) to generate builders for `@AetherRecord` records (complete).
4. **Centralize dependency versions** — move all external (and internal) dependency versions to root `pom.xml` `dependencyManagement`; remove inline versions from child modules and processor paths (complete).
5. **Refactor codegen to FreeMarker** — replace inline `StringBuilder` generation with `Builder.java.ftl` and template model rendering (complete).
6. **Add unit tests** for generated builder logic (e.g., null rejection, regex validation, unmarked records ignored) (complete).
7. **Document usage examples** in `README.md` (complete).
8. After MVP: Add nested/collection support, custom annotations, and OSGi bundle packaging.

---

## 📎 Appendix: Root POM dependency management (template)

```xml
<!-- aether/pom.xml -->
<properties>
  <exceptional.version>1.0.9</exceptional.version>
  <freemarker.version>2.3.34</freemarker.version>
  <jsr269-utilities.version>1.0.1</jsr269-utilities.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.aether</groupId>
      <artifactId>aether-api</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.aether</groupId>
      <artifactId>aether-builder-gen</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.aether</groupId>
      <artifactId>aether-runtime</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.dempsay.support.jsr269</groupId>
      <artifactId>jsr269-utilities</artifactId>
      <version>${jsr269-utilities.version}</version>
    </dependency>
    <dependency>
      <groupId>org.freemarker</groupId>
      <artifactId>freemarker</artifactId>
      <version>${freemarker.version}</version>
    </dependency>
    <dependency>
      <groupId>org.dempsay.utils</groupId>
      <artifactId>exceptional</artifactId>
      <version>${exceptional.version}</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Child module example (no versions on managed deps):

```xml
<dependencies>
  <dependency>
    <groupId>org.aether</groupId>
    <artifactId>aether-api</artifactId>
  </dependency>
  <dependency>
    <groupId>org.freemarker</groupId>
    <artifactId>freemarker</artifactId>
  </dependency>
</dependencies>
```

---

## 📎 Appendix: Annotation Source Template

```java
// aether-api/src/main/java/org/aether/annotations/
package org.aether.annotations;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AetherRecord {}

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.RECORD_COMPONENT)
public @interface Nullable {}

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.RECORD_COMPONENT)
public @interface MinLength { int value(); }

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.RECORD_COMPONENT)
public @interface MaxLength { int value(); }

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.RECORD_COMPONENT)
public @interface RegexMatch { String pattern(); }
```

### FreeMarker Builder Template (excerpt)

```ftl
<#-- aether-builder-gen/src/main/resources/templates/Builder.java.ftl (excerpt) -->
public final class ${builderName} implements AetherBuilder<${recordName}><#if viewInterfaces?has_content>, ...</#if> {
<#list components as c>
    public ${c.typeName} ${c.name}() { return ${c.name}; }
    public ${builderName} ${c.name}(${c.typeName} ${c.name}) { ... }
</#list>
    @Override
    public ExceptionalResponse<${recordName}> build(ExceptionalListener onError) { ... }
}
```

### ValidationException Template

```java
// aether-api/src/main/java/org/aether/validation/
package org.aether.validation;

import java.util.List;

public final class ValidationException extends RuntimeException {
  private final List<String> errors;

  public ValidationException(List<String> errors) {
    super(String.join("; ", errors));
    this.errors = List.copyOf(errors);
  }

  public List<String> getErrors() {
    return errors;
  }
}
```

> ✅ `RetentionPolicy.SOURCE` on annotations ensures no runtime bloat from annotation metadata.