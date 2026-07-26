# Spec: `@OnInsert` / `@OnUpdate` — framework-managed computed values

> Issue: [nativsql#76](https://github.com/heraud/nativsql/issues/76)
> Target version: 2.9.0

## Goal

Let an entity declare a field that the framework automatically recomputes every time
`GenericRepository.update(...)` (`@OnUpdate`) or `GenericRepository.insert(...)`
(`@OnInsert`) is called on that entity — without a DB trigger, so the value stays
testable and mockable.

The initial driver was an `updateDate` timestamp column on `update()`. `@OnInsert` is
the symmetric extension for `insert()` (e.g. a `creationDate` timestamp), added once it
became clear the same "caller-overridable, framework-computed column" need applies to
both operations. The mechanism is not tied to dates: any field whose value should be
derived by the framework on every insert/update (a timestamp, an incrementing version
counter, the id of the user performing the change, a checksum, ...) can use it. The
annotations and provider interface are deliberately generic (`@OnInsert`, `@OnUpdate`,
`ComputedValueProvider<T>`), not date-specific.

## Options considered

| | DB trigger | `@OnUpdate` + hardcoded `Instant.now()` | `@OnUpdate` + injectable value provider (chosen) |
|---|---|---|---|
| Testable / mockable | No — value never visible to JVM before commit | No — can't fix the clock in a test, and can't cover non-date use cases | Yes — provider is swappable per field/class, any type |
| Dialect-independent | No — 4 SQL dialects supported by this project, each with different trigger syntax | Yes | Yes |
| Settable programmatically (migrations, backfills) | No | No | Yes — caller can still pass the column explicitly in `update(entity, "updateDate", ...)` |
| Reusable for non-date fields (version counter, audit user, ...) | No | No | Yes |
| New moving parts | 4x trigger DDL, one per dialect's Liquibase changelog | One annotation + one repository check | One annotation + one provider interface + one repository check |

**Decision:** annotation + injectable provider. This reuses two patterns that already
exist in the codebase rather than inventing new ones:

1. The **`insert()` → `entity.setId(generatedId)`** precedent
   (`GenericRepository.java:218-246`): the framework computes a value the caller
   doesn't supply, and writes it back onto the entity. `@OnUpdate`/`@OnInsert` do the
   same thing, but write the value onto the entity *before* the SQL statement runs
   rather than after (see "Behaviour" below for the tradeoff this implies).
2. The **`@CryptKeyProvider`** pattern (`annotation/type/CryptKeyProvider.java`,
   `crypt/CryptKeyProvider.java`): a field annotation whose `value()` is a
   `Class<? extends SomeFunctionalInterface>`, resolved at read time to an instance
   (Spring bean first, then no-arg constructor via `AnnotationManager.resolveBean`).
   This is exactly the "user-provided init method" Pascal asked for in option 2,
   already built into the framework — no new resolution mechanism needed.

## Behaviour

- A field annotated `@OnUpdate(MyProvider.class)` is set to `MyProvider.getValue()`
  every time `GenericRepository.update(entity, ...)` runs; `@OnInsert(MyProvider.class)`
  does the same for `GenericRepository.insert(entity, ...)`. Both apply **provided the
  caller did not already include that column in the `columns`/`getters` list**.
- If the caller explicitly lists the annotated column
  (`update(entity, "updateDate", ...)` / `insert(entity, "creationDate", ...)`, or the
  `Getter<T>...` equivalents), the caller's own value on the entity is used as-is — no
  override. This is the explicit-override case Pascal asked to keep, useful for
  migrations/backfills that need to set a specific value.
- The computed value is written onto the entity **before** the SQL statement runs (via
  `GenericRepository.applyComputedFields(...)`, shared by both `insert()` and
  `update()`), not after. This means that if the subsequent INSERT/UPDATE fails
  (exception, or — for `update()` — affected row count != 1), the entity is left holding
  the new, uncommitted computed value(s) rather than the value(s) that existed prior to
  the call. This is a deliberate simplicity/consistency tradeoff accepted by Pascal over
  the alternative (write back only after success, mirroring `entity.setId(generatedId)`
  on insert), which required a separate "pending values" map and a post-success
  write-back loop.
- Field type is not constrained by the framework — an entity can use `@OnInsert`/
  `@OnUpdate` for a timestamp (`Instant`, `LocalDateTime`, ...), an `Integer`/`Long` version
  counter, a `String` audit field, etc. Whatever the provider returns must be assignable
  to the field and mappable by the existing `ITypeMapper` machinery, same as any other
  column.
- Multiple independent `@OnUpdate`/`@OnInsert` fields can coexist on the same entity
  (e.g. an `updateDate` timestamp **and** a `version` counter), each with its own
  provider. `@OnInsert` and `@OnUpdate` are independent of each other — a field can carry
  one, the other, both (e.g. `updateDate` written on every update, `creationDate` only
  on insert), or neither.
- No annotation → no behaviour change. Fully opt-in, works on any entity, no base
  class required.
- Auto-applied field values are logged at INFO level via a single call to
  `DbOperationLogger.executeInsert(...)`/`executeUpdate(...)` (format:
  `DB.ONINSERT`/`DB.ONUPDATE <repository> - <table>.{field=value, ...}`), which also runs
  the standard `DB.BEGIN`/`DB.END`/`DB.ERROR` logging by delegating to `execute(...)`
  internally. `GenericRepository.insert()`/`update()` each have a single log-related call
  site — they build the `fieldName -> maskedValue` map for the auto-applied fields and
  pass it to `executeInsert`/`executeUpdate`; the masking (e.g. an encrypted field stays
  masked) reuses the same `convertParamsForLogging` values as the rest of the parameter
  log. This makes the framework's implicit write visible without needing DEBUG-level SQL
  param logging.

## API

### `@OnUpdate` / `@OnInsert` annotations

```java
package ovh.heraud.nativsql.annotation;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnUpdate {
    Class<? extends ComputedValueProvider<?>> value();
}
```

```java
package ovh.heraud.nativsql.annotation;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnInsert {
    Class<? extends ComputedValueProvider<?>> value();
}
```

Neither has a default. The field type is not constrained by the framework (timestamp,
version counter, audit field, ...), so a bare `@OnUpdate`/`@OnInsert` defaulting to a
date-typed provider would silently fail to compile/apply correctly for any
non-`Instant` field. The caller always specifies a provider explicitly, matching
the annotated field's type.

### `ComputedValueProvider<T>` functional interface

```java
package ovh.heraud.nativsql.util;

@FunctionalInterface
public interface ComputedValueProvider<T> {
    T getValue();
}
```

(Named `ComputedValueProvider`, not `UpdateValueProvider`, since it's shared by both
`@OnInsert` and `@OnUpdate` — renamed when `@OnInsert` was added, no longer being
update-specific.)

Resolution mirrors `CryptKeyProvider`: Spring bean first (`applicationContext.getBean(cls)`),
falling back to no-arg constructor instantiation
(`AnnotationManager.resolveBean` — currently `private`, will need to be reused by the
new scanning code; see plan).

No provider ships in `nativsql-core`'s main sources — a timestamp provider is trivial to
write:

```java
public class DateProvider implements ComputedValueProvider<Instant> {
    public Instant getValue() { return Instant.now(); }
}
```

(`SystemComputedValueProvider`, used by this project's own tests and the `User` test
entity, lives in `nativsql-core`'s `testFixtures` source set — it is a test fixture, not
a framework-shipped class, since only test code referenced it.)

Users needing a different behaviour — a mocked clock in tests, a version counter, an
audit field — write their own `ComputedValueProvider<T>` implementation and reference it
in `@OnUpdate(MyProvider.class)`/`@OnInsert(MyProvider.class)`.

### Programmatic registration

Following the `setMappedByInfo`/`setJsonInfo` precedent, for users who prefer config over
annotations (and for tests that want to swap the provider without touching the entity class):

```java
public void setComputedFieldInfo(Class<?> clazz, String fieldName, ComputedValueProvider<?> provider)   // @OnUpdate
public void setOnInsertFieldInfo(Class<?> clazz, String fieldName, ComputedValueProvider<?> provider)    // @OnInsert
```

on `AnnotationManager`.

## Data flow

```
repository.update(entity, "name")
  → GenericRepository.update(T entity, String... columns)
      → annotationManager.getComputedFieldInfos(entityClass)   // cached like mappedByCache
          → finds field(s) annotated @OnUpdate(MyProvider.class)
      → GenericRepository.applyComputedFields(entity, columns, ..., "OnUpdate", ...)
          → for each such field not already in the caller's `columns`:
              provider.getValue() → entityFields.get(fieldName).setValue(entity, value)
              effectiveColumns += fieldName
      → extractValues(entity, effectiveColumns)   // reads the value just written above
      → build SET clause exactly as today, now including the auto-computed column(s)
      → execute UPDATE
```

```
repository.insert(entity, "name")
  → GenericRepository.insert(T entity, String... columns)
      → annotationManager.getOnInsertFieldInfos(entityClass)
          → finds field(s) annotated @OnInsert(MyProvider.class)
      → GenericRepository.applyComputedFields(entity, columns, ..., "OnInsert", ...)   // same helper as update()
      → extractValues(entity, effectiveColumns)
      → build INSERT statement exactly as today, now including the auto-computed column(s)
      → execute INSERT
```

`applyComputedFields(...)` is a single private method shared by `insert()` and
`update()` — they differ only in which computed-field info list (`@OnInsert` vs
`@OnUpdate`) and annotation name (for the null-provider exception message) they pass in.

If the caller already passed a given `@OnUpdate`/`@OnInsert` column in `columns`, none of
the auto-injection happens for that column — `extractValues(entity, columns)` already
reads the caller's own value from the entity, exactly like any other column today.

## Error handling

- Provider class not resolvable (no Spring bean, no no-arg constructor) →
  `NativSQLException`, same message pattern as `CryptKeyProvider` resolution failure.
- Provider returns `null` → `NativSQLException` ("`@OnUpdate`/`@OnInsert` provider must
  not return null"), since a null value defeats the feature's purpose.

## Why `@OnUpdate`/`@OnInsert` are not `TypeParamKey`

`TypeParamKey`/`TypeInfo` carry **per-field value-conversion** metadata (encryption,
JSON, SQL type name, ...), consumed by `ITypeMapper` on every read/write of that field
regardless of which operation triggered it. `@OnUpdate`/`@OnInsert` answer a different
question — *whether a new value should be computed at all, and only for that specific
operation* — decided once per `insert()`/`update()` call, with access to the caller's
requested `columns` (for the explicit-override rule), a context `ITypeMapper`/`TypeInfo`
don't have. Folding it into `TypeParamKey` would leak an insert/update-lifecycle concern
into the value-mapping layer and force every `ITypeMapper` to be aware of a key it never
uses. The class→instance resolution mechanics are still shared
(`AnnotationManager.resolveBean`, the same one `@CryptKeyProvider` uses), but the caches
and consumers are dedicated (`onUpdateCache`/`getComputedFieldInfos` and
`onInsertCache`/`getOnInsertFieldInfos` in `AnnotationManager`, read directly by
`GenericRepository.update()`/`insert()`), following the `@MappedBy` pattern instead.

## Patterns reused

| Pattern | Source |
|---|---|
| "Framework fills a value, then writes it back onto the entity" | `GenericRepository.insert()`, `entity.setId(generatedId)` — `GenericRepository.java:245` |
| Field annotation carrying a `Class<? extends X>` resolved to an instance | `@CryptKeyProvider` — `annotation/type/CryptKeyProvider.java`, `AnnotationManager.resolveBean` (`AnnotationManager.java:272-281`) |
| Per-field annotation cache (`ConcurrentHashMap<FieldKey, ...>`) + programmatic setter override | `mappedByCache`/`getMappedByInfo`/`setMappedByInfo` — `AnnotationManager.java:50,76,291` |
| Superclass field discovery, so an abstract base entity works for free if a user chooses one | `ReflectionUtils.getFields()` (`ReflectionUtils.java:122-136`) |

## Out of scope / explicitly rejected

- DB trigger option — not testable/mockable, dialect-specific (rejected, see table above).
- Seeding `@OnUpdate` fields on `insert()` — still out of scope; `@OnInsert` is a
  separate, independent annotation rather than making `@OnUpdate` fields also apply on
  insert, so an entity's `updateDate` and `creationDate` are controlled independently.
