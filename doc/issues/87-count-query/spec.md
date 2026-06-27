# Spec: CountQuery + count

> Issue: [nativsql#87](https://github.com/heraud/nativsql/issues/87)

## Goal

Introduce a `CountQuery` builder — symmetric to `FindQuery` / `DeleteQuery` — to construct typed `SELECT COUNT(*)` statements with WHERE conditions. Expose the following on `GenericRepository`:

- **`count(CountQuery)`** — `protected`; returns the row count (`long`) matching the WHERE conditions; no row-count assertion (0 is a valid result).
- **`countAll()`** — convenience wrapper, counts every row in the table (no WHERE conditions).
- **`countByProperty(...)`** — convenience wrapper, counts rows where the given property equals the given value.

---

## Architecture

### Data flow

```
newCountQuery().whereAndEquals(User::getStatus, "ACTIVE")
  → CountQuery.buildString(identifierConverter)
      → "SELECT COUNT(*) FROM schema.users\nWHERE\n    status = :status\n"
  → convertParamsToSqlValues({status: "ACTIVE"})
      → TypeMapper pipeline (handles enums, composites, etc.)
  → jdbcTemplate.queryForObject(sql, params, Long.class)  → long count
```

### Key principles

- `CountQuery` holds only a `WhereClause` — no SELECT columns, no ORDER BY, no JOINs, no associations, no LIMIT/OFFSET.
- It extends `AbstractWhereQuery` exactly like `DeleteQuery`, reusing all `whereAnd*` / `whereExpression` methods and the encrypted-column guard for free.
- `count(CountQuery)` never throws for the row count value itself — 0 is a legitimate, valid result.
- `count(CountQuery)` and `newCountQuery()` are `protected` — same visibility as `newFindQuery()` / `newDeleteQuery()`; only the convenience wrappers (`countAll`, `countByProperty`) are `public`, mirroring how `findByProperty` / `deleteByProperty` are the public surface over the protected factories.

---

## API

### `CountQuery<T, ID>`

```java
// Factory
CountQuery.of(repository)

// WHERE conditions (same as FindQuery / DeleteQuery, inherited from AbstractWhereQuery)
.whereAndEquals(String column, Object value)
.whereAndEquals(Getter<T> getter, Object value)
.whereAndIn(String column, List<?> values)
.whereAndIn(Getter<T> getter, List<?> values)
.whereAndOperator(String column, Operator operator, Object value)
.whereAndColumnOperator(String column, ColumnOperator operator)
.whereAndRange(String column, RangeOperator operator, Object low, Object high)
.whereExpression(String expression, String paramName, Object value)

// SQLBuilder
.buildString(IdentifierConverter)
.build(StringBuilder, IdentifierConverter)

// Parameters
.getParameters()         → Map<String, Object>
.hasWhereConditions()    → boolean
```

SQL generated:
```sql
SELECT COUNT(*) FROM schema.table
WHERE
    col = :col
```

(no `WHERE` clause at all when there are no conditions — `SELECT COUNT(*) FROM schema.table`)

### `GenericRepository` additions

```java
// Factory
protected CountQuery<T, ID> newCountQuery()

/**
 * Counts the rows matching the given query.
 * Returns 0 when no rows match — never throws based on the result count.
 */
protected long count(CountQuery<T, ID> query)

/**
 * Counts every row in the table (no WHERE conditions).
 */
public long countAll()

/**
 * Counts rows where the given property equals the given value.
 */
public final long countByProperty(Getter<T> getter, Object value)
public long countByProperty(String property, Object value)
```

---

## Implementation steps

### ⏳ Step 1 — `CountQuery.java`

New file: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/CountQuery.java`

- Extends `AbstractWhereQuery<T, ID, CountQuery<T, ID>>`, mirroring `DeleteQuery`
- Private constructor + static `of(GenericRepository)` factory
- `build(StringBuilder, IdentifierConverter)` generates `SELECT COUNT(*) FROM <table>` plus optional `WHERE …`, same indentation/formatting style as `DeleteQuery`
- `buildString(IdentifierConverter)` convenience wrapper
- `getParameters()` is inherited as-is from `AbstractWhereQuery`

### ⏳ Step 2 — `GenericRepository` additions

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/GenericRepository.java`

Add near `newFindQuery()` / `newDeleteQuery()` (around line 1153-1157) and near the `delete`/`deleteAll` methods:

1. `newCountQuery()` — protected factory, mirrors `newFindQuery()` / `newDeleteQuery()`
2. `count(CountQuery)` — `protected`, builds SQL, converts params via `convertParamsToSqlValues`, executes through `dbOperationLogger.execute(getClass(), "count", "SELECT", getTableName(), sql, params, () -> jdbcTemplate.queryForObject(sql, params, Long.class))`, returns the `long` (unboxing the `Long` result; COUNT(*) always returns exactly one row, so no null-check is needed)
3. `countAll()` — `public`, pure convenience wrapper: `return count(newCountQuery());`
4. `countByProperty(Getter<T>, Object)` / `countByProperty(String, Object)` — `public`, pure convenience wrappers mirroring `deleteByProperty`: `return count(newCountQuery().whereAndEquals(getter/property, value));`

### ⏳ Step 3 — Integration tests (`nativsql-postgres` module)

New class: `nativsql-postgres/src/test/.../repository/postgres/PostgresCountQueryTest.java`

Extends `PostgresRepositoryTest`, imports `PostgresUserRepository`.

```java
@Import({ PostgresUserRepository.class })
class PostgresCountQueryTest extends PostgresRepositoryTest { ... }
```

Since `count(CountQuery)` and `newCountQuery()` are `protected`, exercising a custom `CountQuery` (e.g. `whereAndIn`, multiple conditions) requires `PostgresUserRepository` to expose dedicated public wrapper methods (e.g. `countByStatuses(List<UserStatus>)`, `countByEmailAndStatus(String, UserStatus)`) rather than a generic passthrough — `CountQuery` (and `FindQuery`/`DeleteQuery`) are repository-internal builder types and must never leak into the repository's public API, even for tests. `countAll` / `countByProperty` are public and need no wrapper.

Cases:

| Test | Method | Expected |
|---|---|---|
| `countAll()` | `countAll()` | total row count in table |
| `countByProperty` with getter reference | insert N users with same status, `countByProperty(User::getStatus, ACTIVE)` | N |
| `countByProperty` with string property | `countByProperty("status", "ACTIVE")` | N |
| `countByProperty` with no matching rows | `countByProperty("email", "unknown@x.com")` | 0 |
| count via a custom `CountQuery` with `whereAndIn` (via `countByStatuses` wrapper) | `countByStatuses(List.of(ACTIVE, INACTIVE))` | matching row count |
| count via a custom `CountQuery` with two conditions (AND, via `countByEmailAndStatus` wrapper) | `countByEmailAndStatus(email, status)` | matching row count |
| WHERE on one-way/non-deterministic encrypted column | `countByProperty(encryptedColumn, value)` | `NativSQLException` (guard inherited from `AbstractWhereQuery`) |

### ⏳ Step 4 — Documentation

- **`USERGUIDE.md`** — add a `### Count` section (near `### Delete` / `### Find`) with examples of `count(CountQuery)`, no-condition count, and conditional count
- **`CHANGELOG.md`** — add entry under the appropriate version (ask user whether to bump the version number before writing the entry)

---

## Error handling

| Situation | Method | Behaviour |
|---|---|---|
| 0 rows match | `count(CountQuery)` | returns `0`, no exception |
| WHERE on one-way encrypted column | any `whereAnd*` call | `NativSQLException` from inherited `guardEncryptedColumn` |
| WHERE on non-deterministic encrypted column | any `whereAnd*` call | `NativSQLException` from inherited `guardEncryptedColumn` |

`count` never throws based on the resulting count value — unlike `delete`, there is no "expected exactly 1" semantics here.

---

## Patterns reused

| Pattern | Source |
|---|---|
| `AbstractWhereQuery` (WHERE conditions, encryption guard, parameter collection) | unchanged, reused as-is — same base class as `FindQuery` and `DeleteQuery` |
| `newFindQuery()` / `newDeleteQuery()` factories | mirrored for `newCountQuery()` |
| `dbOperationLogger.execute(...)` | same signature as in `delete` / `deleteAll`, operation label `"SELECT"` (matches existing SELECT-based methods, e.g. `findAll`) |
| `convertParamsToSqlValues` | called identically to `delete`/`deleteAll` |
| `jdbcTemplate.queryForObject(sql, params, Long.class)` | new usage — no existing repository method returns a scalar today; closest precedent is `jdbcTemplate.query(...)` used by `findAll`/`find` |
