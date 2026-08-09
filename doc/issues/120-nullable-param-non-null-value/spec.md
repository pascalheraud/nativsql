# Issue #120 — NullableParam with a non-null value

## Problem

`NullableParam` (introduced in issue #118) let callers declare the SQL type of an
unmapped `findExternal`/`findAllExternal` parameter so PostgreSQL could cast it
(e.g. `(:filterActive)::boolean`). It only supported a `null` value: the single
factory `NullableParam.of(Class<?> type)` had no way to carry an actual value, and
`GenericRepository.convertParamsToSqlValues` unconditionally bound every
`NullableParam` as SQL `null`, discarding any value even if one were added later.

This made `NullableParam` unusable for a parameter whose type can't be inferred
(no matching entity field) but whose value is sometimes non-null — callers had to
fall back to the raw value (losing the cast) or split call sites by nullability.

## Fix

`NullableParam` now has a single factory that takes both the type and the value:

```java
NullableParam.of(Class<?> type, Object value)
```

- `type` is always used to resolve the type mapper/cast (via
  `NamedParamSqlCaster.resolveFieldAccessor`), regardless of whether `value` is
  null or not.
- `value` is bound as-is: `GenericRepository.convertParamsToSqlValues` now checks
  `nullableParam.getValue() != null` — if non-null, it runs the value through the
  normal `convertToSqlValue(...)` conversion (using `type` as the field accessor)
  instead of forcing SQL `null`.

## Call sites

`NullableParam.of(Boolean.class, null)` replaces the old `NullableParam.of(Boolean.class)`.
`NullableParam.of(Boolean.class, true)` is now a valid way to pass a non-null value while
still getting the type cast.

## Files changed

- `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/NullableParam.java` — unified factory.
- `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/GenericRepository.java` — `convertParamsToSqlValues` binds the real value when present instead of forcing `null`.
- Tests: `NullableParamTest`, `NamedParamSqlCasterTest`, `PostgresFindExternalBooleanCastTest`.
