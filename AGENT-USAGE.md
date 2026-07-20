# AGENT-USAGE: Aether Persistence Framework

How to **use** Aether from another Maven / OSGi project (DTOs, builders, store ports).  
For **maintaining this repository**, see [agents.md](agents.md). Product requirements live in [PRD.md](PRD.md) and [PRD-updated.md](PRD-updated.md).

Sibling tooling:

| Need | Doc |
|------|-----|
| OSGi bundle parent (bnd, provided OSGi APIs) | [`dempsay-felix-parent` AGENT-USAGE](../dempsay-felix-parent/AGENT-USAGE.md) |
| Assemble a Felix runtime tree | [`builder-maven-plugin` AGENT-USAGE](../maven-felix-builder/AGENT-USAGE.md) |
| Failure handling (`ExceptionalResponse`) | [exceptional-java WhyBeExceptional](https://github.com/sdempsay/exceptional-java/blob/master/WhyBeExceptional.md) |

---

## What Aether is

Aether is a **compile-time–generated** DTO + persistence-port layer for **Java 21+**:

1. You declare **flat records** with `@AetherRecord` and validation annotations.
2. The **JSR-269 processor** (`aether-builder-gen`) emits:
   - `{Name}Builder` — validated builder returning `ExceptionalResponse<T>`
   - `{Name}Store` — empty interface extending `AetherResourceStore<T>` or `AetherSingletonStore<T>` (for SCR / type-based injection)
3. You persist via **store ports** (`AetherResourceStore` / `AetherSingletonStore`), not a framework-owned global registry.
4. **Providers** are separate artifacts (in-memory in `aether-api`, filesystem in `aether-store-fs`).

**Design goal:** app code depends on **API ports** so unit tests never require a live DB — swap in-memory fakes, temp-dir FS, or allow-all AAA.

**MVP limits:** flat DTOs only (`String`, primitives, wrappers). No nested records or collections. Validation annotations target `String` components.

---

## Coordinates

| Field | Value |
|-------|--------|
| groupId | `org.dempsay.aether` |
| version | Match installed (e.g. `1.1.0-SNAPSHOT`) |
| Java packages | `org.dempsay.aether.*` |

| Artifact | Role | When to depend |
|----------|------|----------------|
| `aether-api` | Annotations, `AetherBuilder`, store ports, in-memory fakes, `ValidationException` | Always (runtime + compile of DTOs) |
| `aether-builder-gen` | Annotation processor | **Processor path only** (never runtime classpath for app logic) |
| `aether-runtime` | Maven aggregator: pulls `aether-api` + `exceptional` | Non-OSGi apps that want one dependency |
| `aether-store-fs` | Filesystem JSON provider (Gson) | When you need FS persistence |

**Prerequisite:** artifacts installed or available in the consumer’s Maven repo (`mvn -DskipDocker install` from this repo for SNAPSHOTs).

---

## Minimal non-OSGi consumer POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <!-- Prefer dempsay-parent or dempsay-felix-parent; versions illustrative -->
  <groupId>org.example</groupId>
  <artifactId>my-app</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <aether.version>1.1.0-SNAPSHOT</aether.version>
    <exceptional.version>1.0.9</exceptional.version>
  </properties>

  <dependencies>
    <!-- Option A: one Maven dep for api + exceptional -->
    <dependency>
      <groupId>org.dempsay.aether</groupId>
      <artifactId>aether-runtime</artifactId>
      <version>${aether.version}</version>
    </dependency>
    <!-- Option B: depend on aether-api + exceptional explicitly (required for OSGi) -->

    <!-- Optional FS backend -->
    <!--
    <dependency>
      <groupId>org.dempsay.aether</groupId>
      <artifactId>aether-store-fs</artifactId>
      <version>${aether.version}</version>
    </dependency>
    -->
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <annotationProcessorPaths>
            <path>
              <groupId>org.dempsay.aether</groupId>
              <artifactId>aether-builder-gen</artifactId>
              <version>${aether.version}</version>
            </path>
            <path>
              <groupId>org.dempsay.aether</groupId>
              <artifactId>aether-api</artifactId>
              <version>${aether.version}</version>
            </path>
            <path>
              <groupId>org.dempsay.utils</groupId>
              <artifactId>exceptional</artifactId>
              <version>${exceptional.version}</version>
            </path>
            <!-- builder-gen also needs freemarker + jsr269-utilities on the processor path
                 if not already transitive in your environment; pin as needed -->
            <path>
              <groupId>org.freemarker</groupId>
              <artifactId>freemarker</artifactId>
              <version>2.3.34</version>
            </path>
            <path>
              <groupId>org.dempsay.support.jsr269</groupId>
              <artifactId>jsr269-utilities</artifactId>
              <version>1.0.1</version>
            </path>
          </annotationProcessorPaths>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

**Do not** put `aether-builder-gen` on the main `dependencies` list for application runtime.

---

## Define DTOs

```java
package org.example.api;

import org.dempsay.aether.annotations.AetherRecord;
import org.dempsay.aether.annotations.MaxLength;
import org.dempsay.aether.annotations.MinLength;
import org.dempsay.aether.annotations.Nullable;
import org.dempsay.aether.annotations.RegexMatch;
import org.dempsay.aether.annotations.Singleton;
import org.dempsay.aether.annotations.Unique;

@AetherRecord
public record UserDto(
    @MinLength(3)
    @MaxLength(50)
    @RegexMatch(pattern = "^[a-zA-Z0-9_]+$")
    @Unique  // unique within store; group defaults to field name
    String username,

    @Nullable
    String nickname
) {}

// One document per store (no id in the multi-resource sense)
@AetherRecord
@Singleton
public record AppConfigDto(
    @MinLength(1)
    String environment
) {}
```

### Annotation cheat sheet

| Annotation | Target | Meaning |
|------------|--------|---------|
| `@AetherRecord` | record type | Opt in to builder + `*Store` generation |
| `@Nullable` | component | May be null; no non-null check |
| `@MinLength` / `@MaxLength` | String component | Length bounds |
| `@RegexMatch` | String component | Full-string pattern match |
| `@Unique` | component | Unique constraint (optional `group`) |
| `@Singleton` | record type | Generate `*Store extends AetherSingletonStore` |

Records **without** `@AetherRecord` get no generated types.

### Generated types (same package as the record)

| Input | Generated |
|-------|-----------|
| `UserDto` | `UserDtoBuilder`, `UserDtoStore` |
| `AppConfigDto` + `@Singleton` | `AppConfigDtoBuilder`, `AppConfigDtoStore` → `AetherSingletonStore<AppConfigDto>` |

---

## Builders and exceptional

Validation failures use **exceptional-java**, not thrown business exceptions from `build(...)`.

```java
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

ExceptionalResponse<UserDto> response = new UserDtoBuilder()
    .username("alice")
    .nickname(null)
    .build(err -> log.error("validation failed", err));

if (response.wasNoError()) {
    UserDto user = response.response();
    // use user
} else {
    // handle failure; do not call response.response() unchecked
}
```

Agents writing dempsay-style code should keep **I/O and store calls** on `ExceptionalResponse` paths — no `throws` on app APIs that fail for expected reasons. See project exceptional rules in [agents.md](agents.md).

---

## Store ports

### Multi-resource (default)

`UserDtoStore extends AetherResourceStore<UserDto>` — HTTP-like create/read/update/delete with id + version etag on update, plus unfiltered **`list`**:

```java
ExceptionalResponse<List<AetherPersisted<UserDto>>> all =
    store.list(onError, principal);
// empty store → success with empty list; order is by resource id
```

No filter, pagination, or query language on `list` (see wishlist T8 / issue #7).

### Singleton

`AppConfigDtoStore extends AetherSingletonStore<AppConfigDto>` — no multi-id collection; single document API.

### In-memory (tests / smoke)

```java
import org.dempsay.aether.access.AetherPrincipal;
import org.dempsay.aether.store.memory.InMemoryAetherResourceStore;

InMemoryAetherResourceStore<UserDto> store =
    new InMemoryAetherResourceStore<>(UserDto.class);

AetherPrincipal principal = /* construct per API */;
// store.create(principal, user) → ExceptionalResponse<AetherPersisted<UserDto>>
```

### Filesystem provider

```java
import org.dempsay.aether.store.fs.FileSystemAetherResourceStore;

// root directory for {type}/{id}.json documents
FileSystemAetherResourceStore<UserDto> store =
    new FileSystemAetherResourceStore<>(rootPath, UserDto.class);
```

Metadata (id, timestamps, version) lives on `AetherPersisted` / `AetherResourceMetadata`, not on the domain record.

**Unit tests:** inject `AetherResourceStore<T>` or the generated `*Store` with an in-memory implementation. Do **not** require Mongo/Postgres for unit tests.

---

## OSGi

Aether jars are built with **`dempsay-felix-parent`** (bnd). Use this section when the consumer is an OSGi bundle or Felix runtime.

### Bundle matrix

| Artifact | Bundle-SymbolicName | Export-Package | Install in framework? |
|----------|---------------------|----------------|------------------------|
| `aether-api` | `org.dempsay.aether.aether-api` | `org.dempsay.aether.access`, `.annotations`, `.builder`, `.failure`, `.store`, `.store.*`, `.validation` | **Yes** |
| `aether-store-fs` | `org.dempsay.aether.aether-store-fs` | `org.dempsay.aether.store.fs` | **Yes**, if using FS |
| `exceptional` | (exceptional’s BSN) | exceptional API packages | **Yes** (api imports it) |
| Gson | third-party OSGi jar | `com.google.gson` | **Yes**, if using `aether-store-fs` |
| `aether-runtime` | `org.dempsay.aether.aether-runtime` | **none** | **No** — empty Maven aggregator |
| `aether-builder-gen` | `org.dempsay.aether.aether-builder-gen` | **none** (private processor packages) | **No** — compile-time only |

Java EE requirement: **JavaSE 21** (`Require-Capability: osgi.ee`).

### OSGi consumer rules

1. **Depend on `aether-api` (+ exceptional), not `aether-runtime`.** Runtime has no useful Export-Package.
2. **Never install `aether-builder-gen` into Felix.** Keep it on `annotationProcessorPaths` at build time only. Generated `*Builder` / `*Store` sources land in the **consumer** bundle.
3. Parent consumer modules from **`dempsay-felix-parent`** so the app module is itself a bundle (see felix-parent AGENT-USAGE).
4. Export **your** packages (`felix.bundle.exportcontents`), e.g. DTO + service API packages. Import-Package for `org.dempsay.aether.*` is calculated by bnd from bytecode — do not force `Import-Package: *` or blanket `resolution:=optional` unless the user asks.
5. Wire SCR by **generated store types** when possible:

   ```java
   // Generated: public interface UserDtoStore extends AetherResourceStore<UserDto> {}
   @Component(service = UserDtoStore.class)
   public class UserDtoStoreImpl extends InMemoryAetherResourceStore<UserDto>
       implements UserDtoStore {
     public UserDtoStoreImpl() {
       super(UserDto.class);
     }
   }
   ```

   Prefer type-based DS references (`@Reference UserDtoStore`) over raw `AetherResourceStore<UserDto>` where SCR erasure is painful.
6. **Provider adapters:** declare types with **`@AetherStoreProviders`** on a **server** `package-info` / marker type and register **`aether-store-gen`** on `annotationProcessorPaths` (plus `aether-store-fs` / memory on the server compile classpath). Generates `Fs{Record}Store` / `Memory{Record}Store` in that package. Optional SCR `@Component` on generated adapters is **T5d**.
7. **Do not unpack / shade `aether-api` into the consumer jar** once proper Export-Package is available. Older `aether-test` unpack hacks were workarounds for pre-bundle aether; prefer Import-Package resolution.
8. FS stack at runtime: install **`aether-api`**, **`exceptional`**, **`aether-store-fs`**, and a **Gson** bundle that exports `com.google.gson` (and stream packages if required).

### Minimal OSGi module sketch

```xml
<parent>
  <groupId>org.dempsay.maven</groupId>
  <artifactId>dempsay-felix-parent</artifactId>
  <version>1.1.0-SNAPSHOT</version>
  <relativePath/>
</parent>

<groupId>org.example</groupId>
<artifactId>my-api</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>jar</packaging>

<properties>
  <aether.version>1.1.0-SNAPSHOT</aether.version>
  <felix.bundle.exportcontents>org.example.api,org.example.api.*</felix.bundle.exportcontents>
</properties>

<dependencies>
  <dependency>
    <groupId>org.dempsay.aether</groupId>
    <artifactId>aether-api</artifactId>
    <version>${aether.version}</version>
  </dependency>
  <dependency>
    <groupId>org.dempsay.utils</groupId>
    <artifactId>exceptional</artifactId>
    <version>1.0.9</version>
  </dependency>
</dependencies>
```

Plus the same **annotationProcessorPaths** as the non-OSGi section (`aether-builder-gen`, api, exceptional, freemarker, jsr269-utilities).

### Felix runtime module

Put Aether + exceptional (+ gson + store-fs if needed) as **direct `compile`** dependencies of a runtime module that uses **builder-maven-plugin** (`download-bundles` only copies direct compile deps). See [maven-felix-builder AGENT-USAGE](../maven-felix-builder/AGENT-USAGE.md).

Your app bundles (DTO/API + impl) are also direct compile deps so they land under `target/felix-runtime/bundle/`.

### Verify Aether bundle headers (from this repo)

```bash
unzip -p aether-api/target/aether-api-*.jar META-INF/MANIFEST.MF
unzip -p aether-store-fs/target/aether-store-fs-*.jar META-INF/MANIFEST.MF
# Expect Bundle-SymbolicName, Bundle-ManifestVersion: 2, Export-Package
```

### OSGi common mistakes

| Mistake | Fix |
|---------|-----|
| Installing `aether-runtime` expecting API packages | Depend on / install **`aether-api`** |
| Installing `aether-builder-gen` into Felix | Processor is **build-time only** |
| Depending only on generic `AetherResourceStore` for SCR | Use generated **`UserDtoStore`** (etc.) as the service type |
| Embedding aether-api classes into the consumer | Import packages from the aether-api bundle |
| Using FS store without Gson in the framework | Install a Gson OSGi bundle |
| Parenting with dempsay-parent only and expecting Export-Package on **your** module | Use **dempsay-felix-parent** for consumer bundles |
| Forcing `Import-Package: *` | Leave bnd defaults |

---

## Server store providers (`@AetherStoreProviders`)

```xml
<!-- server/impl module only -->
<dependency>
  <groupId>org.dempsay.aether</groupId>
  <artifactId>aether-store-fs</artifactId>
  <version>${aether.version}</version>
</dependency>
<dependency>
  <groupId>org.dempsay.aether</groupId>
  <artifactId>aether-store-gen</artifactId>
  <version>${aether.version}</version>
  <!-- annotation + optional compile; SOURCE retention needs no runtime OSGi import -->
</dependency>
```

Register the processor (with builder-gen if this module also defines DTOs — usually DTOs are in api):

```xml
<annotationProcessorPaths>
  <path>
    <groupId>org.dempsay.aether</groupId>
    <artifactId>aether-store-gen</artifactId>
    <version>${aether.version}</version>
  </path>
  <!-- + aether-api / freemarker / jsr269 / exceptional as needed for processor path -->
</annotationProcessorPaths>
```

```java
@AetherStoreProviders(
    filesystem = { UserDto.class },
    singletonFilesystem = { AppConfigDto.class },
    memory = { UserDto.class }
)
package com.example.app.server.stores;

import org.dempsay.aether.store.gen.AetherStoreProviders;
// + DTO imports from api
```

Emits (same package): `FsUserDtoStore`, `FsAppConfigDtoStore`, `MemoryUserDtoStore`.  
Do **not** put this annotation on api DTO packages.

## Agent checklist (consuming Aether)

1. Confirm version: `org.dempsay.aether:*` at the agreed SNAPSHOT/release (e.g. `1.1.0-SNAPSHOT`).
2. Non-OSGi: `aether-runtime` **or** `aether-api` + exceptional; always configure **annotationProcessorPaths** for `aether-builder-gen`.
3. OSGi: `aether-api` + exceptional as **bundles**; never install builder-gen or rely on aether-runtime exports.
4. Annotate flat records with `@AetherRecord`; place validation on components.
5. Use generated `*Builder` → `ExceptionalResponse`; use generated `*Store` for persistence ports.
6. Unit tests: in-memory store (or temp FS); no required live database.
7. Optional FS: `aether-store-fs` + Gson on the runtime classpath / framework.
8. OSGi apps: parent `dempsay-felix-parent`, set `felix.bundle.exportcontents` for **your** public packages, register SCR on `*Store` types if using DS.
9. Build consumer with `mvn -DskipDocker package` (or project norm) and confirm generated sources under `target/generated-sources/annotations` (or equivalent).

---

## Common mistakes (general)

| Mistake | Fix |
|---------|-----|
| Forgetting annotation processor path | Add `aether-builder-gen` (+ api/exceptional/freemarker/jsr269) to **annotationProcessorPaths** |
| Excluding generated sources from checkstyle | Optional for consumers; **aether still must emit checkstyle-clean sources** — file a bug if generated code fails dempsay rules |
| Nested records / `List` fields in MVP | Flat components only until backlog T2/T3 |
| Throwing for validation / store failures | Prefer `ExceptionalResponse` / exceptional utilities |
| Using `aether-store-fs` as the only test backend | Prefer **in-memory** for unit tests |
| Putting processor on runtime `dependencies` | Processor path only |
| groupId `org.aether` or package `org.aether.*` | Correct is **`org.dempsay.aether`** |

---

## Reference consumers

- **aether-test** (sibling): Felix multi-module demo (`org.dempsay.aether.test`) using aether DTOs, in-memory stores, Gogo commands — useful smoke pattern after packages/coordinates stay aligned.
- This repo’s unit tests under `aether-api` / `aether-store-fs` for store behavior examples.

---

## Environment

- **Java 21+** (bytecode and `osgi.ee`).
- Maven 3.6.3+ (3.9+ recommended).
- Install this project first when using SNAPSHOT: from repo root, `mvn -DskipDocker install`.
