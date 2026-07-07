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
| **No external runtime dependencies beyond spec’d artifacts** | Only `exceptional-java` and `jsr269-utilities`. |

---

## 📦 Project Structure (Maven Modules)

```
aether/
├── aether-api/                   # Public API: annotations, ValidationException
│   └── pom.xml (parent: dempsay-parent)
├── aether-builder-gen/           # JSR-269 annotation processor (compile-time only)
│   └── pom.xml (uses jsr269-utilities)
└── aether-runtime/               # MVP: thin runtime dependency aggregator; future: persistence (OSGi-ready bundle)
    └── pom.xml (depends on aether-api + exceptional-java)
```

### Dependencies

| Artifact | Use | Scope |
|--------|------|-------|
| `org.dempsay.maven/dempsay-parent/1.0.4` | Maven parent POM | — |
| `org.dempsay.support.jsr269/jsr269-utilities/1.0.1` | Code generation utilities | `annotationProcessor` only |
| `org.dempsay.utils/exceptional/1.0.9` | Functional error handling (`ExceptionalResponse<T>`) | Runtime (via `aether-runtime`) |

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
  - Generate `*Builder.java` classes with strong typing via `Filer` (string templates; no extra codegen libraries).

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
public final class MyDtoBuilder {
  private String data;

  public MyDtoBuilder() {}

  // Copy constructor (defensive copy not required: builder is mutable, DTOs are immutable)
  public MyDtoBuilder(MyDto source) {
    if (source != null) this.data = source.data();
  }

  // Fluent setter
  public MyDtoBuilder data(String data) {
    this.data = data;
    return this;
  }

  // Validation + build
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
      onError.onError(new ValidationException(errors));
      return ExceptionalResponse.failure();
    }

    return ExceptionalResponse.success(new MyDto(data));
  }
}
```

### Key Design Choices

| Feature | Implementation |
|--------|----------------|
| **Opt-in generation** | `@AetherRecord` marker required; unmarked records are ignored. |
| **Null safety** | Non-null by default; `@Nullable` suppresses null check. |
| **Reusability** | Builder is mutable and reusable (no cloning). |
| **Error reporting** | Collects all validation errors into a single `ValidationException`. |
| **No reflection** | All checks are static string/concrete logic in generated code. |

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

---

## 🧪 Section 4: Validation & Error Handling

### Exception Types
- `ValidationException` in `aether-api` (`org.aether.validation.ValidationException`, extends `RuntimeException`)
  → Contains list of error messages via `getErrors()` returning `List<String>`.

### Builder Contract
```java
// Always returns valid DTO or failure
ExceptionalResponse<MyDto> response = builder.build(listener);
if (response.wasNoError()) {
  MyDto dto = response.safeResponse().orElseThrow();
  // Safe to use dto
} else {
  // ValidationException was delivered to listener.onError(...)
}
```

> ✅ `ExceptionalResponse` (from `exceptional-java`) provides `success()`, `failure()`, `wasError()`, `wasNoError()`, and `safeResponse()`.

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
| Codegen | String templates via `Filer` | No extra dependencies beyond PRD list |
| `aether-runtime` MVP | Thin dependency aggregator | Pulls `aether-api` + `exceptional` for consumers; persistence deferred |

---

## 🚫 Anti-goals (Reaffirmed)

| Goal | Status |
|------|--------|
| No external frameworks beyond listed deps | ✅ Compliant |
| No runtime reflection or proxying | ✅ Compliant via codegen |
| OSGi support deferred but design must not prevent future bundle conversion | ✅ Modular structure ensures this |

---

## 📌 Next Steps

1. **Sync PRD** with planning decisions (complete).
2. **Scaffold Maven modules** (`aether-api`, `aether-builder-gen`, `aether-runtime`).
3. **Implement JSR-269 processor** (`aether-builder-gen`) to generate builders for `@AetherRecord` records.
4. **Add unit tests** for generated builder logic (e.g., null rejection, regex validation, unmarked records ignored).
5. **Document usage examples** in `README.md`.
6. After MVP: Add nested/collection support, custom annotations, and OSGi bundle packaging.

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