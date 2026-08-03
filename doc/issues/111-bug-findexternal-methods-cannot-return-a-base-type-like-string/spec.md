# Spec: `findExternal`/`findAllExternal` support a base type as result class for single-column queries

> Issue: [nativsql#111](https://github.com/heraud/nativsql/issues/111)

## Goal

`GenericRepository.findExternal(...)` / `findAllExternal(...)` are meant to map a custom SQL
query onto a **result entity** (a plain class with fields, used for reporting/aggregation
queries whose shape differs from the repository's own entity `T`) — see
`GenericRepository.java:1650-1721`.

Today, passing a **base/scalar type** as `resultClass` (`String`, `Long`, `Integer`, `UUID`,
...) compiles and runs, but fails deep in the mapping layer with a confusing error, because
`GenericRowMapper` tries to instantiate the result class via a public no-arg constructor and
copy column values into its fields — `String`/`Long`/`Integer`/... have neither:

```
NativSQLException: Failed to map row to Long
  Caused by: ReflectiveOperationException (no default constructor / IllegalAccessException)
```

This is a real, common use case though:

```java
Long count = findExternal("select count(*) from users", Long.class);
List<Integer> ids = findAllExternals("select id from users where active = true", Integer.class);
```

Both queries return exactly **one column**. The fix: when the query's `ResultSet` has exactly
one column and `resultClass` is a base/scalar type, map that single column's value directly to
`resultClass` — no bean introspection, no fields. When the query returns more than one column and
`resultClass` is a base type, the mapping is genuinely ambiguous (which column would it use?), so
raise one clear, purpose-built `NativSQLException` instead of the current confusing reflection
failure.

---

## Design decision: column count is only known per-row, so the check moves from "class-shape" to "row-shape"

An earlier draft of this spec rejected any base-type `resultClass` outright, unconditionally,
before the query even ran — on the theory that `findExternal`/`findAllExternal` only make sense
for entity-shaped results. That's wrong: single-column scalar queries (`COUNT(*)`, `SELECT id
FROM ...`, `SELECT MAX(price) ...`) are a legitimate, common use of these methods, and forcing
callers to wrap every scalar in a throwaway bean class is unnecessary ceremony the framework can
avoid.

Because the number of columns a query returns is only known once the `ResultSet` executes (not
from `resultClass` alone), the validation cannot happen purely against the Java `Class` up front
— it has to happen where `ResultSetMetaData` is available, i.e. inside a `RowMapper.mapRow(...)`
call, per row:

- `resultClass` is a base type **and** `ResultSetMetaData.getColumnCount() == 1` → map that one
  column directly to `resultClass` via the dialect's existing scalar `ITypeMapper` (the same
  mappers already used for entity fields — `LongTypeMapper`, `StringTypeMapper`, etc.)
- `resultClass` is a base type **and** the query returns more than one column → ambiguous, throw
  `NativSQLException` naming the type and the actual column count
- `resultClass` is not a base type → unchanged, existing bean-introspection `GenericRowMapper`
  path

`RowMapperFactory` already knows whether a class is a base type via `dialect.getMapperForType`
(`GenericDialect.java:129-163`, returns non-null exactly for scalar/JDBC types, `null` for
bean/entity types) — reused as-is, not duplicated.

---

## Architecture

### New `ScalarRowMapper<T>`

A new `RowMapper<T>` implementation, sibling to `GenericRowMapper`, in the same
`ovh.heraud.nativsql.mapper` package:

```java
public class ScalarRowMapper<T> implements RowMapper<T> {

    private final Class<T> resultClass;
    private final ITypeMapper<T> typeMapper;

    public ScalarRowMapper(Class<T> resultClass, ITypeMapper<T> typeMapper) {
        this.resultClass = resultClass;
        this.typeMapper = typeMapper;
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        int columnCount = rs.getMetaData().getColumnCount();
        if (columnCount != 1) {
            throw new NativSQLException(
                    "findExternal/findAllExternal with result type '" + resultClass.getSimpleName()
                            + "' requires the query to return exactly one column, but it returned "
                            + columnCount + ". Use an entity/bean result class for multi-column queries.");
        }
        String columnLabel = rs.getMetaData().getColumnLabel(1);
        return typeMapper.map(rs, columnLabel, null, Collections.emptyMap());
    }
}
```

- `typeMapper.map(rs, columnLabel, null, Map.of())` reuses `AbstractTypeMapper.map(...)` exactly
  as `GenericRowMapper.mapColumn` does for entity fields (`GenericRowMapper.java:146-148`) — the
  `fieldAccessor` argument is only used for contextual error messages/encryption-key lookups by
  concrete mappers, and every base-type mapper (`LongTypeMapper`, `StringTypeMapper`, ...)
  ignores it when absent (confirmed: none of the base-type `fromValue` implementations
  dereference `fieldAccessor`), so passing `null` here is safe.
- No `encrypted` param key is set, so the plain (non-encrypted) path in
  `AbstractTypeMapper.map` is taken — scalar `findExternal` results are never decrypted, which
  matches today's behavior (there's no field/annotation to carry `@Encrypted` on a raw scalar
  query result).

### `RowMapperFactory` branches on base type

`RowMapperFactory.getRowMapper`/`createRowMapper` (`RowMapperFactory.java:43-92`) gets one new
branch before the existing bean-introspection path:

```java
public <T> RowMapper<T> getRowMapper(Class<T> clazz, DatabaseDialect dialect,
        IdentifierConverter identifierConverter) {
    @SuppressWarnings("unchecked")
    RowMapper<T> cached = (RowMapper<T>) cache.get(clazz);
    if (cached != null) {
        return cached;
    }
    RowMapper<T> mapper = dialect.getMapperForType(clazz) != null
            ? new ScalarRowMapper<>(clazz, dialect.getMapperForType(clazz))
            : createRowMapper(clazz, dialect, identifierConverter);
    cache.put(clazz, mapper);
    return mapper;
}
```

The cache's value type and the method's return type widen from `GenericRowMapper<?>`/
`GenericRowMapper<T>` to `RowMapper<?>`/`RowMapper<T>` (the Spring interface both
`GenericRowMapper` and `ScalarRowMapper` already implement) — no caller-visible change, since
`findAllExternal` already only requires a `RowMapper<EXT>` (`GenericRepository.java:1719`,
`jdbcTemplate.query(sql, params, rowMapperFactory.getRowMapper(...))`).

This new branch only triggers for **root-level** `getRowMapper` calls (i.e. from
`findExternal`/`findAllExternal`) with a base-type `resultClass` — it does not affect the
recursive joined-property call at `RowMapperFactory.java:82-85`, since that call only happens for
a field whose declared type has no mapper as a **field** (`dialect.getMapper(fieldAccessor, ...)
== null`, checked via `FieldAccessor`), which by construction is never a base type.

### `DatabaseDialect.getMapperForType` promoted to the interface

`GenericDialect.getMapperForType(Class<T>)` (currently `protected`,
`GenericDialect.java:129-163`) is promoted to `DatabaseDialect` as a new interface method:

```java
/**
 * Returns the scalar TypeMapper for a base/JDBC type (String, Long, Integer, UUID,
 * LocalDate, ...), or null if the type is not a base type (i.e. it's an entity/bean
 * whose fields should be introspected instead).
 */
<T> ITypeMapper<T> getMapperForType(Class<T> targetType);
```

`GenericDialect`'s existing implementation becomes the (now public) interface implementation,
unchanged in content. All other dialects (`PostgresDialect`, `MariaDBDialect`, `OracleDialect`)
inherit it via `AbstractChainedDialect`/`GenericDialect`, same as `getMapper` itself — no new
per-dialect code.

### Data flow

```
findAllExternal("select count(*) as count from users", Map.of(), Long.class)
  → rowMapperFactory.getRowMapper(Long.class, dialect, identifierConverter)
      → dialect.getMapperForType(Long.class) → LongTypeMapper (non-null)
      → new ScalarRowMapper<>(Long.class, longTypeMapper)
  → jdbcTemplate.query(sql, params, scalarRowMapper)
      → mapRow(rs, 0): columnCount == 1 → longTypeMapper.map(rs, "count", null, Map.of()) → 42L
  → List.of(42L)
```

```
findAllExternal("select id, name from users", Map.of(), Long.class)
  → ScalarRowMapper.mapRow(rs, 0): columnCount == 2
      → throw NativSQLException("findExternal/findAllExternal with result type 'Long' requires
         the query to return exactly one column, but it returned 2. Use an entity/bean result
         class for multi-column queries.")
```

---

## API

### `DatabaseDialect` (new method)

```java
<T> ITypeMapper<T> getMapperForType(Class<T> targetType);
```

Promoted from `GenericDialect`'s existing `protected` method — same behavior, now part of the
dialect contract so `RowMapperFactory` can call it without depending on `GenericDialect`
specifically.

### `ScalarRowMapper<T>` (new, `ovh.heraud.nativsql.mapper` package)

```java
public class ScalarRowMapper<T> implements RowMapper<T> {
    public ScalarRowMapper(Class<T> resultClass, ITypeMapper<T> typeMapper)
    public T mapRow(ResultSet rs, int rowNum) throws SQLException
}
```

### `RowMapperFactory` (behavior change, no public signature change beyond return-type widening)

`getRowMapper` returns `RowMapper<T>` instead of `GenericRowMapper<T>` (both already implement
Spring's `RowMapper<T>`, and `findAllExternal` only ever consumed it as a `RowMapper`, so this is
not a breaking change for callers).

### `GenericRepository` — no signature or behavior change

`findExternal`/`findAllExternal` (`GenericRepository.java:1650-1721`) are unchanged; the new
behavior is entirely inside `rowMapperFactory.getRowMapper(...)`, which they already call.

---

## Error handling

| Situation | Behaviour |
|---|---|
| `resultClass` is a base type, query returns exactly 1 column | value mapped directly via the dialect's scalar `ITypeMapper`, same conversions as for entity fields (e.g. `Long` accepts any `Number`, `String`, or `Boolean`) |
| `resultClass` is a base type, query returns 0 or ≥2 columns | `NativSQLException` naming the type and the actual column count, raised from `ScalarRowMapper.mapRow` on the first row (or never thrown if the query returns zero rows — nothing to map) |
| `resultClass` is `byte[]` | treated as a base type (already in `getMapperForType`'s list) — same single-column rule applies |
| `resultClass` is an entity/bean class | unchanged — existing `GenericRowMapper` bean-introspection path, regardless of column count |
| A single-column scalar query returns a `null` value (e.g. `MAX(price)` on an empty table) | mapper returns `null` for that row, same as today's `null`-passthrough in `AbstractTypeMapper.map` (`AbstractTypeMapper.java:54-55`) |

---

## Patterns reused

| Pattern | Source |
|---|---|
| Base-type detection | `GenericDialect.getMapperForType` (`GenericDialect.java:129-163`), promoted to the `DatabaseDialect` interface, no new type list |
| Scalar value mapping (`ITypeMapper.map(rs, columnLabel, null, params)`) | same call already used per-column in `GenericRowMapper.mapColumn` (`GenericRowMapper.java:146-148`); reused with `fieldAccessor = null` since base-type mappers don't dereference it |
| `RowMapper<T>` as the shared return type | Spring interface already implemented by `GenericRowMapper`; `ScalarRowMapper` is a sibling implementation, not a subclass |
| Cache keyed by result class | existing `RowMapperFactory.cache` (`RowMapperFactory.java:25`), unchanged, now holds either mapper kind |

## Tests

Following existing conventions (`GenericRepositoryFindAsSubtypeTest.java` and similar), add unit
tests on a repository subclass exposing `findExternal`/`findAllExternal` as public wrappers:

- `findExternal("select count(*) as count from t", Long.class)` on a single-column query →
  returns the mapped `Long`, no exception
- `findAllExternals("select id from t", Integer.class)` → returns `List<Integer>` matching the
  rows
- `findExternal("select id, name from t", Long.class)` (2 columns) →
  `assertThatThrownBy(...).isInstanceOf(NativSQLException.class)`, message mentions `Long` and
  the column count `2`
- `findExternal("select count(*) from t where 1=0", Long.class)` when the aggregate yields
  `null` → returns `null`, no exception (0 rows would return `null`/empty list as today; the
  `null`-aggregate case still has exactly 1 column, just a `null` value)
- `findExternal(sql, SomeReportEntity.class)` → unchanged behavior, no exception, still maps rows
  via `GenericRowMapper` (regression guard)
