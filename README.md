# Aether Persistence Framework

Aether is a minimal, annotation-driven, compile-time-generated persistence DTO layer for Java 21+. It generates validated builders for flat record DTOs with zero runtime reflection.

**Design goal:** code against API ports so applications stay **unit-testable without a running database** — swap in fakes or lightweight providers (and test-friendly AAA) instead of requiring MongoDB, PostgreSQL, and the like just to run tests. See `PRD.md` core principles and `PRD-updated.md`.

## Modules

| Module | Purpose | OSGi |
|--------|---------|------|
| `aether-api` | Annotations, builders, store ports, in-memory fakes | Bundle; exports `org.dempsay.aether.*` API packages |
| `aether-builder-gen` | JSR-269 annotation processor (compile-time only) | Bundle metadata only; **not** for OSGi runtime install |
| `aether-runtime` | Maven dependency aggregator (`aether-api` + exceptional) | Empty jar / no exports — use `aether-api` in OSGi |
| `aether-store-fs` | Filesystem JSON store provider (Gson) | Bundle; exports `org.dempsay.aether.store.fs` |

## Quick Start

Add `aether-runtime` and register the annotation processor:

```xml
<dependency>
  <groupId>org.dempsay.aether</groupId>
  <artifactId>aether-runtime</artifactId>
  <version>1.1.0-SNAPSHOT</version>
</dependency>

<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>
        <groupId>org.dempsay.aether</groupId>
        <artifactId>aether-builder-gen</artifactId>
        <version>1.1.0-SNAPSHOT</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

## Define a DTO

```java
import org.dempsay.aether.annotations.AetherRecord;
import org.dempsay.aether.annotations.MaxLength;
import org.dempsay.aether.annotations.MinLength;
import org.dempsay.aether.annotations.Nullable;

@AetherRecord
public record UserDto(
    @MinLength(3) @MaxLength(50) String username,
    @Nullable String nickname
) {}
```

Records without `@AetherRecord` do not receive a generated builder.

## Use the Generated Builder

Aether uses [exceptional-java](https://github.com/sdempsay/exceptional-java) for validation failures. See [WhyBeExceptional.md](https://github.com/sdempsay/exceptional-java/blob/master/WhyBeExceptional.md) for the design rationale.

```java
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

ExceptionalResponse<UserDto> response = new UserDtoBuilder()
    .username("alice")
    .nickname(null)
    .build(err -> logger.error("Validation failed", err));

if (response.wasNoError()) {
    UserDto user = response.response();
    // use user
}
```

## Gson Serialization

DTO records serialize with Gson without extra adapters:

```java
String json = new Gson().toJson(user); // {"username":"alice","nickname":null}
```

## MVP Limitations

- Flat DTOs only: `String`, primitives, and wrappers
- No nested records, collections, or arrays
- `@AetherRecord` required for builder generation
- Validation annotations apply to `String` components only

## OSGi

Parent: `org.dempsay.maven:dempsay-felix-parent` (bnd + provided OSGi APIs). See that repo’s `AGENT-USAGE.md`.

| Bundle | Symbolic name | Exports |
|--------|---------------|---------|
| `aether-api` | `org.dempsay.aether.aether-api` | access, annotations, builder, failure, store (+ memory/unique), validation |
| `aether-store-fs` | `org.dempsay.aether.aether-store-fs` | `org.dempsay.aether.store.fs` |
| `aether-runtime` | `org.dempsay.aether.aether-runtime` | none (Maven-only aggregator) |
| `aether-builder-gen` | `org.dempsay.aether.aether-builder-gen` | none (compile-time processor) |

**OSGi consumer checklist**

1. Install **`aether-api`** and **`exceptional`** (Import-Package from api).
2. Optional FS provider: install **`aether-store-fs`** and a **Gson** OSGi bundle (Import-Package `com.google.gson`).
3. Do **not** install `aether-builder-gen` into the framework — use it only on the Maven compiler `annotationProcessorPaths`.
4. Prefer **`aether-api`** over `aether-runtime` as the OSGi dependency; runtime is for non-OSGi Maven convenience.
5. Generated `*Store` interfaces (T5a) are for app SCR wiring; framework provider SCR adapters are T5b–T5d.

Verify headers after package:

```bash
unzip -p aether-api/target/aether-api-*.jar META-INF/MANIFEST.MF
```

## Build

```bash
mvn -f pom.xml verify
```

Requires `dempsay-felix-parent:1.1.0-SNAPSHOT` (→ `dempsay-parent:1.0.4`), `jsr269-utilities:1.0.1`, and `exceptional:1.0.9` in your Maven repository.

Tests are enabled by `aether-builder-gen/src/test/resources/tests.md`, which activates JUnit Jupiter via `dempsay-parent`.

## License

See [PRD.md](PRD.md) for full product requirements.