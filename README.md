# Aether Persistence Framework

Aether is a minimal, annotation-driven, compile-time-generated persistence DTO layer for Java 21+. It generates validated builders for flat record DTOs with zero runtime reflection.

**Design goal:** code against API ports so applications stay **unit-testable without a running database** — swap in fakes or lightweight providers (and test-friendly AAA) instead of requiring MongoDB, PostgreSQL, and the like just to run tests. See `PRD.md` core principles and `PRD-updated.md`.

## Modules

| Module | Purpose |
|--------|---------|
| `aether-api` | Annotations and `ValidationException` |
| `aether-builder-gen` | JSR-269 annotation processor (compile-time only) |
| `aether-runtime` | Runtime dependency aggregator for consumers |

## Quick Start

Add `aether-runtime` and register the annotation processor:

```xml
<dependency>
  <groupId>org.aether</groupId>
  <artifactId>aether-runtime</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>

<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>
        <groupId>org.aether</groupId>
        <artifactId>aether-builder-gen</artifactId>
        <version>0.1.0-SNAPSHOT</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

## Define a DTO

```java
import org.aether.annotations.AetherRecord;
import org.aether.annotations.MaxLength;
import org.aether.annotations.MinLength;
import org.aether.annotations.Nullable;

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

## Build

```bash
mvn -f pom.xml verify
```

Requires `dempsay-parent:1.0.4`, `jsr269-utilities:1.0.1`, and `exceptional:1.0.9` in your Maven repository.

Tests are enabled by `aether-builder-gen/src/test/resources/tests.md`, which activates JUnit Jupiter via `dempsay-parent`.

## License

See [PRD.md](PRD.md) for full product requirements.