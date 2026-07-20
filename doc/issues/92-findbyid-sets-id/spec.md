# Spec: findById/findAllByIds always set the entity id

> Issue: [nativsql#92](https://github.com/heraud/nativsql/issues/92)

## Goal

Methods that look up an entity by its id (`findById`, `findAllByIds`, and their
`Getter<T>...` overloads) must always return entities with the `id` field populated,
even when the caller did not explicitly include the id column in the requested
properties.

## Problem

`GenericRepository.findById(Object id, String... columns)` and
`findAllByIds(List<?> ids, String... columns)` build a `FindQuery` selecting
**only** the columns passed by the caller
(`nativsql-core/src/main/java/ovh/heraud/nativsql/repository/GenericRepository.java:523-531` and `:561-569`).

If the caller does not explicitly request the id column (e.g. `findById(id, "name")`),
the id column is absent from the SQL `SELECT`, so it never appears in the JDBC
`ResultSet`. `GenericRowMapper.mapRow` only maps columns actually present in
`ResultSetMetaData`
(`nativsql-core/src/main/java/ovh/heraud/nativsql/mapper/GenericRowMapper.java:52-91`),
so the entity's `id` field is left at its default value (`null`/`0`) — even though the
id is already known, since it's the very criterion used to look the entity up.

## Behaviour after fix

- `findById(id, "name")` → returned entity has `id` set to the looked-up id, in addition
  to `name`.
- `findAllByIds(ids, "name")` → every returned entity has its own `id` set correctly,
  in addition to `name`.
- Requesting the id column explicitly (e.g. `findById(id, "id", "name")`) continues to
  work exactly as before — no duplicate column in the generated SQL, no exception.
- No change to any other lookup method (`findByProperty`, `findAllByProperty`,
  `find(FindQuery)`, `findAll(FindQuery)`, etc.) — they keep returning exactly the
  columns requested, with the id only present if explicitly asked for.

## Architecture

### Data flow

```
findById(42, "name")
  → ensureIdColumnSelected(["name"]) → ["name", "id"]   (dedup, case-insensitive on ID_COLUMN)
  → newFindQuery().select("name", "id").whereAndEquals(ID_COLUMN, 42)
  → find(query)
      → SQL: SELECT name, id FROM ... WHERE id = :id
      → GenericRowMapper maps both columns as usual (id is no longer a "missing" column)
  → entity.getName() == "..."  &&  entity.getId() == 42
```

### Key principles

- The fix lives entirely in `GenericRepository.findById` / `findAllByIds`: always
  ensure `ID_COLUMN` is part of the columns passed to `FindQuery.select(...)`.
- No change to `FindQuery.select(...)` or `GenericRowMapper` — their general contract
  (map exactly the columns present in the ResultSet) is unchanged and still applies to
  every other lookup method.
- Deduplication must be case-insensitive-safe consistent with how column/property names
  are otherwise compared in the repository (`ID_COLUMN` is the camelCase property name
  `"id"`, compared against caller-supplied property names in `columns`).
- The `Getter<T>...` overloads (`findById(Object, Getter<T>...)`,
  `findAllByIds(List<ID>, Getter<T>...)`) already delegate to the `String...` overloads
  via `ReflectionUtils.getColumnNames(getters)`, so they get the fix for free.

## API

No public API signature changes. Behavioural change only:

```java
// GenericRepository<T, ID>
public final T findById(Object id, Getter<T>... getters)          // unchanged signature
public T findById(Object id, String... columns)                    // unchanged signature, id now always populated
public final List<T> findAllByIds(List<ID> ids, Getter<T>... getters) // unchanged signature
public List<T> findAllByIds(List<?> ids, String... columns)        // unchanged signature, id now always populated
```

## Error handling

No new error cases. Existing validation (`columns == null || columns.length == 0` →
`NativSQLException`) is unchanged and still checked on the caller-supplied array,
before the id column is injected.

## Patterns reused

| Pattern | Source |
|---|---|
| `ID_COLUMN` constant | `GenericRepository.java:55`, already used by `whereAndEquals`/`whereAndIn` in both target methods |
| `FindQuery.select(String...)` | unchanged, just called with an extra column |
| `GenericRowMapper` column-driven mapping | unchanged, naturally picks up the id once selected |
